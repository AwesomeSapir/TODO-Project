package com.example.httpspringserver;

import org.json.JSONObject;

public class Todo {
    private final int id;
    private final String title;
    private final String content;
    private final long dueDate;
    private Status status;

    public Todo(int id, JSONObject json){
        this.id = id;
        this.title = json.getString("title");
        this.content = json.getString("content");
        this.dueDate = json.getLong("dueDate");
        this.status = Todo.Status.PENDING;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getDueDate() {
        return dueDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public JSONObject toJson(){
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("title", title);
        json.put("content", content);
        json.put("status", status.toString());
        json.put("dueDate", dueDate);
        return json;
    }

    // Enum for TODO status
    public enum Status {
        PENDING,
        LATE,
        DONE
    }

}
