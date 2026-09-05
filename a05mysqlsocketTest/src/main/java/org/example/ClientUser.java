package org.example;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class ClientUser {

    private Socket socket;
    private  String username;
    private OutputStream out;

    public ClientUser(Socket socket, String username) throws IOException {
        this.socket = socket;
        this.username = username;
        this.out = socket.getOutputStream();

    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Socket getSocket() {
        return socket;
    }

    public void setSocket(Socket socket) {
        this.socket = socket;
    }
    public OutputStream getOut() {
        return out;
    }
}
