package com.thesis.todo.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/public")
public class PublicController {

	@Operation(operationId = "getAppInfo", summary = "Thông tin ứng dụng (công khai)")
	@GetMapping("/info")
	public Map<String, String> info() {
		return Map.of("name", "To-do sample app", "version", "0.1.0");
	}

}
