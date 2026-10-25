package com.aiyi.game.dnfserver.service.dof;

public class DofFailure extends RuntimeException {
    public final int status;
    public DofFailure(int status, String message) { super(message); this.status = status; }
}
