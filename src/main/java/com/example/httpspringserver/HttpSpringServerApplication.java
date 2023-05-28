package com.example.httpspringserver;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@SpringBootApplication
@RestController
@RequestMapping("/todo")
public class HttpSpringServerApplication {

    private static final Logger requestLogger = LogManager.getLogger("request-logger");
    private static final Logger todoLogger = LogManager.getLogger("todo-logger");
    private static int idCounter = 1; // Counter for generating unique todo IDs
    private static final Map<Integer, Todo> todos = new HashMap<>(); // Map to store todos with their IDs as keys

    private JSONObject createJsonResponse(Object result, String errorMessage) {
        JSONObject jsonResponse = new JSONObject();
        if (result != null) {
            jsonResponse.put("result", result);
        }
        if (errorMessage != null) {
            jsonResponse.put("errorMessage", errorMessage);
        }
        return jsonResponse;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(HttpSpringServerApplication.class);
        app.setDefaultProperties(Collections.singletonMap("server.port", "8496"));
        app.run(args);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        logger.info("Received request for health endpoint");
        String response = "OK";
        logger.info("Sending response: {}", response);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<String> createTodo(@RequestBody String requestBody){
        logger.info("Received request to create TODO: {}", requestBody);
        JSONObject json = new JSONObject(requestBody);
        Todo todo = new Todo(idCounter++, json);

        ResponseEntity<String> response = null;

        for (Todo itrTodo : todos.values()) {
            if(itrTodo.getTitle().equals(todo.getTitle())) {
                response = ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .body(createJsonResponse(null, "Error: TODO with the title [" + todo.getTitle() + "] already exists in the system").toString());
                idCounter--;
            }
        }

        if (response == null && todo.getDueDate() <= System.currentTimeMillis()) {
            response = ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(createJsonResponse(null, "Error: Can't create new TODO that its due date is in the past").toString());
            idCounter--;
        }

        if (response == null){
            response = ResponseEntity.ok(createJsonResponse(todo.getId(), null).toString());
            todos.put(todo.getId(), todo);
        }
        logger.info("Sending response: {}", response);
        return response;
    }

    @GetMapping("/size")
    public ResponseEntity<String> getTodosCount(@RequestParam("status") String requestStatus){
        logger.info("Received request to get TODOs count with status: {}", requestStatus);
        ResponseEntity<String> response;

        if (requestStatus.equals("ALL")){
            response = ResponseEntity.ok(createJsonResponse(todos.size(), null).toString());
        } else {
            try {
                Todo.Status status = Todo.Status.valueOf(requestStatus);
                long count = todos.values().stream().filter(todo -> todo.getStatus() == status).count();
                response = ResponseEntity.ok(createJsonResponse(count, null).toString());
            } catch (IllegalArgumentException e){
                response = ResponseEntity.badRequest().build();
            }
        }
        logger.info("Sending response: {}", response);
        return response;
    }

    @GetMapping("/content")
    public ResponseEntity<String> getTodosContent(@RequestParam("status") String requestStatus,
                                                  @RequestParam(value = "sortBy", required = false) String requestSortBy){
        logger.info("Received request to get TODOs content with status: {} and sortBy: {}", requestStatus, requestSortBy);

        Comparator<Todo> comparator = Comparator.comparing(Todo::getId);
        if (requestSortBy != null) {
            switch (requestSortBy) {
                case "DUE_DATE" -> comparator = Comparator.comparing(Todo::getDueDate);
                case "TITLE" -> comparator = Comparator.comparing(Todo::getTitle);
                case "ID" -> comparator = Comparator.comparing(Todo::getId);
                default -> {
                    logger.error("Invalid sortBy: {}", requestSortBy);
                    return ResponseEntity.badRequest().build();
                }
            }
        }

        Stream<Todo> stream = todos.values().stream()
                .sorted(comparator);
        if (!requestStatus.equals("ALL")){
            try {
                Todo.Status status = Todo.Status.valueOf(requestStatus);
                stream = stream.filter(todo -> todo.getStatus() == status);
            } catch (IllegalArgumentException e){
                logger.error("Invalid status: {}", requestStatus);
                return ResponseEntity.badRequest().build();
            }
        }
        List<Todo> sortedList = stream.toList();
        JSONArray jsonArray = new JSONArray();
        for (Todo todo : sortedList){
            jsonArray.put(todo.toJson());
        }
        ResponseEntity<String> response = ResponseEntity.ok(createJsonResponse(jsonArray, null).toString());
        logger.info("Sending response: {}", response);
        return response;
    }

    @PutMapping
    public ResponseEntity<String> updateTodoStatus(@RequestParam("id") int requestId, @RequestParam("status") String requestStatus){
        logger.info("Received request to update status for TODO with ID {}: {}", requestId, requestStatus);

        Todo.Status prevStatus;
        Todo todo = todos.get(requestId);
        if(todo != null){
            prevStatus = todo.getStatus();
            try {
                Todo.Status status = Todo.Status.valueOf(requestStatus);
                todo.setStatus(status);
                todos.replace(requestId, todo);
            } catch (IllegalArgumentException e) {
                logger.error("Invalid status: {}", requestStatus);
                return ResponseEntity.badRequest().build();
            }
        }else {
            logger.error("Todo not found for id: {}", requestId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(createJsonResponse(null, "Error: no such TODO with id " + requestId).toString());
        }

        ResponseEntity<String> response = ResponseEntity.ok(createJsonResponse(prevStatus, null).toString());
        logger.info("Sending response: {}", response);
        return response;
    }

    @DeleteMapping
    public ResponseEntity<String> deleteTodo(@RequestParam("id") int requestId){
        logger.info("Received request to delete TODO with ID: {}", requestId);

        Todo todo = todos.get(requestId);
        if(todo != null){
            todos.remove(requestId);
        } else {
            logger.error("Todo not found for id: {}", requestId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(createJsonResponse(null, "Error: no such TODO with id " + requestId).toString());
        }

        ResponseEntity<String> response = ResponseEntity.ok(createJsonResponse(todos.size(), null).toString());
        logger.info("Sending response: {}", response);
        return response;
    }
}
