package com.bgsoftware.ssboneblock.utils;

import com.bgsoftware.ssboneblock.OneBlockModule;
import com.bgsoftware.ssboneblock.actions.Action;
import com.bgsoftware.ssboneblock.actions.ActionType;
import com.bgsoftware.ssboneblock.actions.CommandAction;
import com.bgsoftware.ssboneblock.actions.MultiAction;
import com.bgsoftware.ssboneblock.actions.RandomAction;
import com.bgsoftware.ssboneblock.actions.SetBlockAction;
import com.bgsoftware.ssboneblock.actions.SpawnEntityAction;
import com.bgsoftware.ssboneblock.actions.container.ContainerPoll;
import com.bgsoftware.ssboneblock.error.ParsingException;
import com.bgsoftware.ssboneblock.handler.PhasesHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JsonUtils {

    private static final Gson gson = new GsonBuilder().create();

    private JsonUtils() {

    }

    public static Gson getGson() {
        return gson;
    }

    public static Optional<Action> getAction(JsonObject actionObject, PhasesHandler phasesHandler, String fileName) throws ParsingException {
        // Handle nested "actions" arrays
        if (actionObject.has("actions")) {
            List<Action> multipleActions = new ArrayList<>();
            
            JsonElement actionsElement = actionObject.get("actions");
            
            if (!(actionsElement instanceof JsonArray))
                throw new ParsingException("Section \"actions\" must be a list.");
            
            for (JsonElement _actionElement : (JsonArray) actionsElement) {
                getAction(_actionElement.getAsJsonObject(), phasesHandler, fileName)
                        .ifPresent(multipleActions::add);
            }
            
            return multipleActions.isEmpty() ? Optional.empty() : 
                    Optional.of(new MultiAction(multipleActions.toArray(new Action[0])));
        }
        
        JsonElement actionElement = actionObject.get("action");

        if (!(actionElement instanceof JsonPrimitive))
            throw new ParsingException("Missing \"action\" section.");

        String action = actionElement.getAsString();
        ActionType actionType;

        try {
            actionType = ActionType.valueOf(action.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ParsingException("Invalid action-type \"" + action + "\".");
        }

        switch (actionType) {
            case SET_BLOCK:
                return SetBlockAction.fromJson(actionObject, phasesHandler, fileName);
            case RANDOM:
                return RandomAction.fromJson(actionObject, phasesHandler, fileName);
            case COMMAND:
                return CommandAction.fromJson(actionObject);
            case SPAWN_ENTITY:
                return SpawnEntityAction.fromJson(actionObject);
            default:
                throw new ParsingException("Invalid action-type \"" + action + "\".");
        }
    }

    public static Action[] getActionsArray(JsonArray jsonArray, PhasesHandler phasesManager, String fileName) {
        List<Action> actionList = new ArrayList<>();

        for (JsonElement actionElement : jsonArray) {
            JsonObject actionObject = actionElement.getAsJsonObject();

            if (actionObject.has("actions")) {
                JsonElement actionsElement = actionObject.get("actions");

                if (!(actionsElement instanceof JsonArray))
                    throw new IllegalArgumentException("Section \"actions\" must be a list.");

                Action[] innerActions = getActionsArray((JsonArray) actionsElement, phasesManager, fileName);

                if (actionObject.has("repeat")) {
                    // Has "repeat": flatten inner actions into a sequence, repeat the whole sequence
                    int repeatCount = actionObject.get("repeat").getAsInt();
                    for (int r = 0; r < repeatCount; r++) {
                        for (Action inner : innerActions)
                            actionList.add(inner);
                    }
                } else {
                    // No "repeat": wrap in MultiAction so all inner actions run together on one break
                    Action combined = innerActions.length == 1 ? innerActions[0] : new MultiAction(innerActions);
                    actionList.add(combined);
                }
            } else {
                Action action = getActionSafely(actionObject, phasesManager, fileName).orElse(null);

                if (action == null)
                    continue;

                int amountOfActions = actionObject.has("amount") ? actionObject.get("amount").getAsInt() : 1;

                for (int i = 0; i < amountOfActions; i++)
                    actionList.add(action);
            }
        }

        return actionList.toArray(new Action[0]);
    }

    public static ContainerPoll[] getContainerItems(JsonArray jsonArray, String fileName) {
        List<ContainerPoll> polls = new ArrayList<>();

        for (JsonElement jsonElement : jsonArray) {
            polls.add(ContainerPoll.fromJson((JsonObject) jsonElement, fileName));
        }

        return polls.toArray(new ContainerPoll[0]);
    }

    public static <T> T parseFile(File file, Class<T> classOf) throws IOException {
        StringBuilder data = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null)
                data.append(line).append("\n");
        }

        return gson.fromJson(data.toString(), classOf);
    }

    private static Optional<Action> getActionSafely(JsonObject jsonObject, PhasesHandler phasesManager, String fileName) {
        try {
            return JsonUtils.getAction(jsonObject, phasesManager, fileName);
        } catch (ParsingException error) {
            OneBlockModule.log("[" + fileName + "] " + error.getMessage());
            error.printStackTrace();
            return Optional.empty();
        }
    }

}
