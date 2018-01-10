package chatroom.server.controller;

import chatroom.common.entity.User;
import chatroom.common.message.*;
import chatroom.server.dto.Login;
import chatroom.server.dto.Register;
import chatroom.common.entity.Visitor;
import chatroom.server.model.MessageService;
import chatroom.server.model.UserService;
import chatroom.server.model.impl.MessageServiceImpl;
import chatroom.server.model.impl.UserServiceImpl;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import static chatroom.common.message.Iconst.*;

public class ServerController {
    /**
     * 消息服务
     */
    private MessageService messageService = new MessageServiceImpl();
    /**
     * 用户服务
     */
    private UserService userService = new UserServiceImpl();

    /**
     * 启动服务器
     */
    public void startup() {
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(10000);
            System.out.println("服务器正在监听 10000 端口 ...");
            while (true) {
                serve(serverSocket.accept());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 为客户端提供持续的
     * @param inputStream 接收第一个消息前创建的（根据第一个消息决定是否提供持续响应）
     * @param socket
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void continuedResponse(ObjectInputStream inputStream, Socket socket) throws IOException, ClassNotFoundException {
        System.out.println("准备持续为此Socket提供服务······");
        while (true) {
            Object object = inputStream.readObject();
            Message message = (Message) object;

            if (message.getType() == PUBLIC_MESSAGE) {
                messageService.sendAll(socket, message);
            }

            if (message.getType() == PERSONAL_MESSAGE) {
                // 分两种情况：对方在线和对方不再线
                Long target = message.getTarget();
                if (userService.getSocket(target) != null) {
                    // 在线转发
                    Socket targetSocket = userService.getSocket(target);
                    final String originContent = message.getContent(); // 不加 final 只是传引用！暂时还没有思考其它方法。
                    StringBuilder builder = new StringBuilder();
                    builder.append(userService.getUser(message.getSource()).getNickname() + "悄悄对你说：");
                    builder.append(originContent + "\n");

                    message.setContent(builder.toString());
                    messageService.send(targetSocket, message);

                    StringBuilder echo = new StringBuilder();
                    echo.append("你悄悄对" + userService.getUser(target).getNickname() + "说：");
                    echo.append(originContent + "\n");

                    message.setContent(echo.toString());
                    messageService.send(socket, message);
                } else {
                    message.setContent("对方不在线哦！\n");
                    messageService.send(socket, message);
                    // 不在线缓存消息

                }
            }
        }
    }

    /**
     * 多线程为每个客户端提供服务
     * @param socket
     */
    private void serve(final Socket socket) {
        new Thread(new Runnable() {
            @Override
            public void run() {

                System.out.println(socket.hashCode() + "接入服务器······");

                ObjectInputStream inputStream = null;

                try {
                    inputStream = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));
                    System.out.println("服务器输入流创建完毕，等待客户端的第一条消息······");
                    // 响应该 socket 的第一条消息
                    Object object = inputStream.readObject();
                    Message firstRequest = (Message) object;
                    Message firstResponse = null;

                    // 游客
                    if (firstRequest.getType() == VISITOR_ACCESS) {
                        VisitorLogin visitorLogin = (VisitorLogin) firstRequest;
                        String nickname = visitorLogin.getNickname();

                        Visitor visitor = new Visitor(nickname);
                        messageService.addAcceptor(socket, visitor);
                        System.out.println("昵称为" + nickname + "的游客等登陆服务器······");

                        firstResponse = Message.ok("ok");
                        messageService.send(socket, firstResponse);

                        continuedResponse(inputStream, socket);
                    }

                    // 注册
                    if (firstRequest.getType() == GENERAL_REGISTER) {

                        MsgRegister msgRegister = (MsgRegister) firstRequest;
                        String username = msgRegister.get("username");
                        String password = msgRegister.get("password");

                        System.out.println(username + "&" + password + "准备注册");

                        Register reg = new Register(username, password);
                        firstResponse = userService.register(reg);

                        messageService.send(socket, firstResponse);
                    }

                    // 登陆
                    if (firstRequest.getType() == GENERAL_LOGIN) {

                        MsgLogin msgLogin = (MsgLogin) firstRequest;
                        String username = msgLogin.get("username");
                        String password = msgLogin.get("password");

                        System.out.println(username + "&" + password + "准备登陆");

                        Login login = new Login(username, password);
                        firstResponse = userService.login(socket, login);

                        messageService.send(socket, firstResponse);

                        // 初始化信息: 个人信息， 好友列表/状态， 缓存消息
                        if (firstResponse.getFlag()) {
                            User user = userService.getUser(socket);
                            messageService.addAcceptor(socket, user);
                            messageService.send(socket, new MsgProfile(user));
                            System.out.println("用户个人信息已经发出" + user);

                            List<User> userList = userService.getFriendList(user.getUserId());
                            if (userList != null && userList.size() > 0) {
                                messageService.send(socket, new MsgFriends(userList));
                                System.out.println("用户好友列表已经发出" + userList);
                            }
                            continuedResponse(inputStream, socket);
                        }
                    }

                    throw new IOException();

                } catch (ClassNotFoundException notFoundException) {
                    messageService.deleteAcceptor(socket);
                    messageService.closeOutputStream(socket);
                    userService.logout(socket);
                    System.out.println(socket.hashCode() + "已正常下线。ClassNotFound\n");
                } catch (IOException ioException) {
                    messageService.deleteAcceptor(socket);
                    messageService.closeOutputStream(socket);
                    userService.logout(socket);
                    System.out.println(socket.hashCode() + "已正常下线。IOException\n");
                } finally {
                    try {
                        if (inputStream != null) {
                            inputStream.close();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }
        }).start();
    }
}
