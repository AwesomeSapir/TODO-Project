package com.example.httpspringserver;

import org.apache.logging.log4j.ThreadContext;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootApplication
@RestController
@RequestMapping("/todo")
public class HttpSpringServerApplication {

    private static final Logger requestLogger = LogManager.getLogger("request-logger");
    private static final Logger todoLogger = LogManager.getLogger("todo-logger");
    private static int idCounter = 1; // Counter for generating unique todo IDs
    private static int requestCounter = 0;// Counter for unique requests

    private static long requestStart = 0;

    private static final Map<Integer, Todo> todos = new HashMap<>(); // Map to store todos with their IDs as keys

    public enum eHttpVerb {
        GET, POST, DELETE, PUT
    }

    private void logRequest(eHttpVerb httpVerb){
        requestCounter++;
        ThreadContext.put("requestId", String.valueOf(requestCounter));
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        String endpoint = null;
        if (requestAttributes != null) {
            endpoint = requestAttributes.getRequest().getRequestURI();
        }
        requestLogger.info("Incoming request | #" + requestCounter + " | resource: " + endpoint + " | HTTP Verb " + httpVerb.toString());
        requestStart = System.currentTimeMillis();
        ThreadContext.clearAll();
    }

    private void logRequestEnd(){
        ThreadContext.put("requestId", String.valueOf(requestCounter));
        requestLogger.debug("request #" + requestCounter + " duration: " + (System.currentTimeMillis() - requestStart) + "ms");
        ThreadContext.clearAll();
    }

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
        app.setDefaultProperties(Collections.singletonMap("server.port", "9583"));
        app.run(args);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        logRequest(eHttpVerb.GET);

        String response = "OK";

        logRequestEnd();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<String> createTodo(@RequestBody String requestBody){
        logRequest(eHttpVerb.POST);

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

        logRequestEnd();
        return response;
    }

    @GetMapping("/size")
    public ResponseEntity<String> getTodosCount(@RequestParam("status") String requestStatus){
        logRequest(eHttpVerb.GET);

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

        logRequestEnd();
        return response;
    }

    @GetMapping("/content")
    public ResponseEntity<String> getTodosContent(@RequestParam("status") String requestStatus,
                                                  @RequestParam(value = "sortBy", required = false) String requestSortBy){
        logRequest(eHttpVerb.GET);

        Comparator<Todo> comparator = Comparator.comparing(Todo::getId);
        if (requestSortBy != null) {
            switch (requestSortBy) {
                case "DUE_DATE" -> comparator = Comparator.comparing(Todo::getDueDate);
                case "TITLE" -> comparator = Comparator.comparing(Todo::getTitle);
                case "ID" -> comparator = Comparator.comparing(Todo::getId);
                default -> {
                    //logger.error("Invalid sortBy: {}", requestSortBy);
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
                //logger.error("Invalid status: {}", requestStatus);
                return ResponseEntity.badRequest().build();
            }
        }
        List<Todo> sortedList = stream.toList();
        JSONArray jsonArray = new JSONArray();
        for (Todo todo : sortedList){
            jsonArray.put(todo.toJson());
        }
        ResponseEntity<String> response = ResponseEntity.ok(createJsonResponse(jsonArray, null).toString());

        logRequestEnd();
        return response;
    }

    @PutMapping
    public ResponseEntity<String> updateTodoStatus(@RequestParam("id") int requestId, @RequestParam("status") String requestStatus){
        logRequest(eHttpVerb.PUT);

        Todo.Status prevStatus;
        Todo todo = todos.get(requestId);
        if(todo != null){
            prevStatus = todo.getStatus();
            try {
                Todo.Status status = Todo.Status.valueOf(requestStatus);
                todo.setStatus(status);
                todos.replace(requestId, todo);
            } catch (IllegalArgumentException e) {
                //logger.error("Invalid status: {}", requestStatus);
                return ResponseEntity.badRequest().build();
            }
        }else {
            //logger.error("Todo not found for id: {}", requestId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(createJsonResponse(null, "Error: no such TODO with id " + requestId).toString());
        }

        ResponseEntity<String> response = ResponseEntity.ok(createJsonResponse(prevStatus, null).toString());

        logRequestEnd();
        return response;
    }

    @DeleteMapping
    public ResponseEntity<String> deleteTodo(@RequestParam("id") int requestId){
        logRequest(eHttpVerb.DELETE);

        Todo todo = todos.get(requestId);
        if(todo != null){
            todos.remove(requestId);
        } else {
            //logger.error("Todo not found for id: {}", requestId);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(createJsonResponse(null, "Error: no such TODO with id " + requestId).toString());
        }

        ResponseEntity<String> response = ResponseEntity.ok(createJsonResponse(todos.size(), null).toString());

        logRequestEnd();
        return response;
    }
}
