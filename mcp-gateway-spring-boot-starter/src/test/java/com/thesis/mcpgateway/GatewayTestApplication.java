package com.thesis.mcpgateway;

import java.util.Map;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ứng dụng nhỏ chỉ dùng cho test của Starter. Không có Spring Security.
 */
@SpringBootApplication
public class GatewayTestApplication {

	@RestController
	static class TestController {

		@GetMapping("/api/hello")
		Map<String, String> hello() {
			return Map.of("message", "hello");
		}

		@GetMapping("/api/slow")
		Map<String, String> slow() throws InterruptedException {
			Thread.sleep(2000);
			return Map.of("message", "too late");
		}

		@DeleteMapping("/api/items/{id}")
		void deleteItem(@PathVariable long id) {
		}

	}

}
