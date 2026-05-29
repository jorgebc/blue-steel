package com.bluesteel.application.port.in.user;

import com.bluesteel.application.model.user.UserProfile;
import java.util.List;
import java.util.UUID;

/** Admin or any-campaign GM: looks up existing platform users by exact email (D-043, D-064). */
public interface SearchUsersUseCase {

  List<UserProfile> searchByEmail(String email, UUID callerId, boolean callerIsAdmin);
}
