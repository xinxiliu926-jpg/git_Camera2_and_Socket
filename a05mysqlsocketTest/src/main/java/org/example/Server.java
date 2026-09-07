package org.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Server {
    public static final List<ClientUser> ONLINE_SOCKET_LIST = new ArrayList<>();
    public static final Map<String, ClientUser> User_Map = new HashMap<>();
    public static final Object LOCK = new Object();

    
    public static final ArrayList<Socket> VIDEO_CLIENTS =new ArrayList<>();
   
    private static final int VIDEO_PORT = 9000;


    public static void main(String[] args) throws IOException {

        ServerSocket ss = new ServerSocket(10000);
        System.out.println("服务器已经开启，等待连接");

        while (true) {
            Socket accept = ss.accept();
            System.out.println("服务器连接成功" + accept.getInetAddress());
            System.out.println("新客户端连接" + accept.getInetAddress());
            ClientHandler clientHandler = new ClientHandler(accept);
            Thread thread = new Thread(clientHandler);
            thread.start();
        }
    }



    }
