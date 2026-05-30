package com.bluesteel.application.port.in.session;

import com.bluesteel.application.model.session.CommitSessionCommand;

/** Driving port: validates and commits a reviewed session diff to world state. */
public interface CommitSessionUseCase {

  void commit(CommitSessionCommand command);
}
