package com.thanhnien.rss.controller;

import com.thanhnien.rss.model.ChatRequest;
import com.thanhnien.rss.model.ChatResponse;
import com.thanhnien.rss.service.ChatbotService;
import com.thanhnien.rss.service.OpenAIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chatbot")
@CrossOrigin(origins = "*")
public class ChatbotController {

    @Autowired
    private ChatbotService chatbotService;

    @Autowired
    private OpenAIService openAIService;

    /**
     * Chat endpoint - process user message and return news summary
     * 
     * Example request:
     * POST /api/chatbot/chat
     * {"message": "hôm nay bản tin thời sự có gì?"}
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request,
            jakarta.servlet.http.HttpSession session) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    ChatResponse.builder()
                            .message("Vui lòng nhập câu hỏi của bạn.")
                            .articleCount(0)
                            .articles(Collections.emptyList())
                            .build());
        }

        ChatResponse response = chatbotService.processMessage(request.getMessage(), session);
        return ResponseEntity.ok(response);
    }

    /**
     * Quick summary endpoint - get summary for a specific category
     * 
     * Example: GET /api/chatbot/summary/thoi-su
     */
    @GetMapping("/summary/{category}")
    public ResponseEntity<ChatResponse> getSummary(@PathVariable String category) {
        ChatResponse response = chatbotService.getQuickSummary(category);
        return ResponseEntity.ok(response);
    }

    /**
     * Get chatbot status and capabilities
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("active", true);
        status.put("aiEnabled", openAIService.isConfigured());
        status.put("supportedCategories", new String[] {
                "thoi-su", "cong-nghe", "the-thao", "kinh-te",
                "giai-tri", "giao-duc", "du-lich", "suc-khoe",
                "the-gioi", "doi-song", "xe", "chinh-tri", "tieu-dung", "van-hoa"
        });
        status.put("exampleQueries", new String[] {
                "Hôm nay bản tin thời sự có gì?",
                "Tin công nghệ mới nhất hôm nay",
                "Có tin thể thao nào không?",
                "Tóm tắt tin kinh tế trong ngày"
        });
        return ResponseEntity.ok(status);
    }
}
