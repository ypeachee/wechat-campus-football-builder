package com.campusfootball.team;

import com.campusfootball.user.UserRole;

public class TeamManagerAssignment {
    private long userId;
    private UserRole roleCode;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public UserRole getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(UserRole roleCode) {
        this.roleCode = roleCode;
    }
}