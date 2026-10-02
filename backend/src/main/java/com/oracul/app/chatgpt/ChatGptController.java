package com.oracul.app.chatgpt;

import com.oracul.app.api.ChatgptApi;
import com.oracul.app.api.model.ChatGptConnection;
import com.oracul.app.api.model.ChatGptConnectionState;
import com.oracul.app.session.CurrentSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatGptController implements ChatgptApi {

    // Providers keep the controller loadable in MVC-only test slices that have no service beans.
    private final ObjectProvider<ChatGptAuthService> services;
    private final ObjectProvider<CurrentSession> sessions;

    ChatGptController(ObjectProvider<ChatGptAuthService> services, ObjectProvider<CurrentSession> sessions) {
        this.services = services;
        this.sessions = sessions;
    }

    @Override
    public ResponseEntity<Void> startChatGptSignIn() {
        return redirect(services.getObject().start(sessions.getObject().id()));
    }

    @Override
    public ResponseEntity<Void> completeChatGptSignIn(String code, String state, String error,
                                                      String errorDescription, String clientId) {
        ChatGptAuthService service = services.getObject();
        return redirect(service.redirectTo(service.complete(code, state, error, errorDescription, clientId)));
    }

    @Override
    public ResponseEntity<ChatGptConnection> getChatGptConnection() {
        ChatGptAuthService.State state = services.getObject().connectionState(sessions.getObject().id());
        return ResponseEntity.ok(new ChatGptConnection(
            ChatGptConnectionState.fromValue(state.name()), state == ChatGptAuthService.State.CONNECTED));
    }

    @Override
    public ResponseEntity<Void> disconnectChatGpt() {
        services.getObject().disconnect(sessions.getObject().id());
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }
}
