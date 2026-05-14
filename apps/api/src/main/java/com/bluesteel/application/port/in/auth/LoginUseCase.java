package com.bluesteel.application.port.in.auth;

import com.bluesteel.application.model.auth.LoginCommand;
import com.bluesteel.application.model.auth.LoginResult;

public interface LoginUseCase {

  LoginResult login(LoginCommand command);
}
