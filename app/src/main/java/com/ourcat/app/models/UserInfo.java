package com.ourcat.app.models;

import com.google.gson.annotations.SerializedName;

public class UserInfo {

    private long id;
    private String username;
    private String nickname;
    @SerializedName("avatarUrl")
    private String avatarUrl;
    private String bio;
    private int role;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public int getRole() { return role; }
    public void setRole(int role) { this.role = role; }
}
