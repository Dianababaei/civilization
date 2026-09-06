package sut.civilization.Network.Messages;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON <-> Message. The shared "type" field tells the receiver which concrete
 * class to deserialize into. Adding a new message kind = one new subclass plus
 * one register() line here; no existing code changes (Open/Closed).
 */
public class MessageCodec {
    private static final Gson gson = new Gson();
    private static final Map<String, Class<? extends Message>> TYPES = new HashMap<>();

    static {
        register(JoinLobbyRequest.TYPE, JoinLobbyRequest.class);
        register(SetReadyRequest.TYPE, SetReadyRequest.class);
        register(ChatSendRequest.TYPE, ChatSendRequest.class);
        register(ChooseMapRequest.TYPE, ChooseMapRequest.class);
        register(StartGameRequest.TYPE, StartGameRequest.class);
        register(JoinResultResponse.TYPE, JoinResultResponse.class);
        register(ErrorResponse.TYPE, ErrorResponse.class);
        register(LobbyStateBroadcast.TYPE, LobbyStateBroadcast.class);
        register(ChatBroadcast.TYPE, ChatBroadcast.class);
        register(GameStartingBroadcast.TYPE, GameStartingBroadcast.class);
        register(EndTurnRequest.TYPE, EndTurnRequest.class);
        register(MoveUnitRequest.TYPE, MoveUnitRequest.class);
        register(ActionResultResponse.TYPE, ActionResultResponse.class);
        register(GameStateBroadcast.TYPE, GameStateBroadcast.class);
        register(AttackRequest.TYPE, AttackRequest.class);
        register(DeclareWarRequest.TYPE, DeclareWarRequest.class);
        register(OfferAllianceRequest.TYPE, OfferAllianceRequest.class);
        register(AllianceReplyRequest.TYPE, AllianceReplyRequest.class);
        register(BreakAllianceRequest.TYPE, BreakAllianceRequest.class);
        register(TradeOfferRequest.TYPE, TradeOfferRequest.class);
        register(TradeReplyRequest.TYPE, TradeReplyRequest.class);
        register(BattleReportMessage.TYPE, BattleReportMessage.class);
        register(TrainUnitRequest.TYPE, TrainUnitRequest.class);
        register(DebugActionRequest.TYPE, DebugActionRequest.class);
        register(UpgradeTownHallRequest.TYPE, UpgradeTownHallRequest.class);
        register(ResearchRequest.TYPE, ResearchRequest.class);
        register(BuildImprovementRequest.TYPE, BuildImprovementRequest.class);
        register(StationRequest.TYPE, StationRequest.class);
        register(CheatCommandRequest.TYPE, CheatCommandRequest.class);
    }

    private static void register(String type, Class<? extends Message> clazz) {
        TYPES.put(type, clazz);
    }

    /** One message = one line of compact JSON (the transport sends line by line). */
    public static String encode(Message message) {
        return gson.toJson(message);
    }

    public static Message decode(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        if (object.get("type") == null)
            throw new IllegalArgumentException("Message has no type field: " + json);
        String type = object.get("type").getAsString();
        Class<? extends Message> clazz = TYPES.get(type);
        if (clazz == null)
            throw new IllegalArgumentException("Unknown message type: " + type);
        return gson.fromJson(object, clazz);
    }
}
