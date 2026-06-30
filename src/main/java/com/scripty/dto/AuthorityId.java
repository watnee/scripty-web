package com.scripty.dto;

import java.io.Serializable;
import java.util.Objects;

public class AuthorityId implements Serializable {

    private String username;
    private String authority;

    public AuthorityId() {}

    public AuthorityId(String username, String authority) {
        this.username = username;
        this.authority = authority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthorityId that = (AuthorityId) o;
        return Objects.equals(username, that.username) && Objects.equals(authority, that.authority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, authority);
    }
}
