# SS02_HW05_IT213_DangKhanhAn_PTIT070
## 1. Lớp `FeedbackAnalysisService`

```java
package com.rlogistics.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
public class FeedbackAnalysisService {

    private final ChatModel chatModel;

    // Constructor Injection
    public FeedbackAnalysisService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String analyzeFeedback(String feedbackText) {

        if (feedbackText == null || feedbackText.isBlank()) {
            throw new IllegalArgumentException(
                    "Nội dung phản hồi không được để trống"
            );
        }

        String promptText = """
                Hãy phân tích phản hồi của khách hàng dưới đây.

                Yêu cầu:
                - Xác định cảm xúc: tích cực, tiêu cực hoặc trung lập.
                - Tóm tắt vấn đề chính.
                - Đề xuất hướng xử lý cho bộ phận chăm sóc khách hàng.

                Phản hồi của khách hàng:
                %s
                """.formatted(feedbackText);

        Prompt prompt = new Prompt(promptText);

        ChatResponse response = chatModel.call(prompt);

        return response
                .getResult()
                .getOutput()
                .getText();
    }
}
```

`chatModel.call(prompt)` trả về `ChatResponse`; nội dung văn bản được lấy từ `response.getResult().getOutput().getText()`. Đây là cách gọi được mô tả trong [Spring AI ChatModel API](https://docs.spring.io/spring-ai/reference/api/chatmodel.html).

## 2. Vì sao đây là thiết kế Loosely Coupled?

Service chỉ phụ thuộc vào interface:

```java
private final ChatModel chatModel;
```

Thay vì phụ thuộc trực tiếp vào implementation:

```java
private final OllamaChatModel chatModel;
```

hoặc:

```java
private final OpenAiChatModel chatModel;
```

`ChatModel` là abstraction chung. Cả `OllamaChatModel` và `OpenAiChatModel` đều triển khai interface này.

Vì vậy:

* Service không cần biết mô hình AI đang chạy local hay cloud.
* Có thể đổi từ Ollama sang OpenAI/OpenRouter bằng cấu hình.
* Không cần sửa phương thức `analyzeFeedback()`.
* Dễ viết unit test bằng cách mock `ChatModel`.
* Giảm sự phụ thuộc giữa logic nghiệp vụ và nhà cung cấp AI.

Đây chính là nguyên tắc **Programming to Interface**: lớp nghiệp vụ phụ thuộc vào interface, còn implementation cụ thể sẽ được Spring Dependency Injection lựa chọn và inject vào lúc chạy.

## 3. Xử lý xung đột nhiều bean

Nếu cả `OllamaChatModel` và `OpenAiChatModel` cùng tồn tại, Spring nhìn thấy nhiều bean triển khai `ChatModel` và có thể báo:

```text
NoUniqueBeanDefinitionException
```

### Cách 1: Sử dụng `@Profile` — nên dùng

Tạo bean đại diện cho model đang hoạt động theo từng profile:

```java
package com.rlogistics.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
public class ChatModelConfig {

    @Bean
    @Primary
    @Profile("local")
    public ChatModel localChatModel(OllamaChatModel ollamaChatModel) {
        return ollamaChatModel;
    }

    @Bean
    @Primary
    @Profile("cloud")
    public ChatModel cloudChatModel(OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }
}
```

Kích hoạt mặc định profile local:

```properties
# application.properties
spring.profiles.active=local
```

Cấu hình local:

```properties
# application-local.properties
spring.ai.model.chat=ollama
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=qwen2.5-coder:7b
```

Cấu hình cloud:

```properties
# application-cloud.properties
spring.ai.model.chat=openai
spring.ai.openai.base-url=https://openrouter.ai/api
spring.ai.openai.api-key=${OPENROUTER_API_KEY}
spring.ai.openai.chat.model=google/gemini-2.5-flash
```

Chạy cloud bằng:

```bash
./gradlew bootRun --args='--spring.profiles.active=cloud'
```

Ở mỗi lần chạy chỉ có profile tương ứng được kích hoạt. Spring sẽ inject model được đánh dấu `@Primary`.

Lưu ý: Trong các phiên bản Spring AI mới, `spring.ai.model.chat=ollama` hoặc `spring.ai.model.chat=openai` được dùng để chọn auto-configuration của ChatModel, thay cho thuộc tính `*.chat.enabled` cũ. Xem [Ollama configuration](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) và [OpenAI configuration](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html).

### Cách 2: Chỉ sử dụng `@Primary`

Nếu muốn cả hai bean cùng tồn tại nhưng Ollama là mặc định:

```java
@Bean
@Primary
public ChatModel primaryChatModel(
        OllamaChatModel ollamaChatModel
) {
    return ollamaChatModel;
}
```

Khi inject:

```java
public FeedbackAnalysisService(ChatModel chatModel)
```

Spring sẽ ưu tiên bean có `@Primary`.

Kết luận: `@Profile` phù hợp khi muốn chuyển môi trường local/cloud; `@Primary` phù hợp khi nhiều model cùng tồn tại nhưng cần chọn một model mặc định.
