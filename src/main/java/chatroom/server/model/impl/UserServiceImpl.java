package chatroom.server.model.impl;

import chatroom.common.Message;
import chatroom.server.dao.UserDAO;
import chatroom.server.dto.Login;
import chatroom.server.dto.Register;
import chatroom.server.entity.User;
import chatroom.server.model.UserService;

public class UserServiceImpl implements UserService {
    private UserDAO userDAO;

    public UserServiceImpl() {

    }

    @Override
    public Message register(Register reg) {
        User user = new User();
        user.setUsername(reg.getUsername());
        user.setPassword(reg.getPassword());
        if (userDAO.saveUser(user)) {
            return Message.ok("ok");
        } else {
            return Message.fail("fail");
        }
    }

    @Override
    public Message login(Login login) {
        String username = login.getUsername();
        String password = login.getPassword();
        User user = userDAO.getUserByUsername(username);
        if (user.getPassword() != null && !user.getPassword().equals("")) {
            if (user.getPassword().equals(password)) {

            } else {

            }
        } else {

        }
        return null;
    }

    @Override
    public Message logout() {
        return null;
    }

    @Override
    public void sendPublicMessage() {

    }
}
