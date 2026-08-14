# Bài 5: Kiến trúc & Code - Thiết kế Loosely Coupled

## 1. Mã nguồn `FeedbackAnalysisService`

```java
package com.rlogistics.cskh.service;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
public class FeedbackAnalysisService {

    private final ChatModel chatModel;

    public FeedbackAnalysisService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String analyzeFeedback(String feedbackText) {
        String prompt = """
                Bạn là chuyên viên phân tích phản hồi khách hàng của R-Logistics.
                Hãy phân tích nội dung phản hồi sau và trả về kết quả ngắn gọn gồm:
                1. Cảm xúc của khách hàng
                2. Vấn đề chính
                3. Mức độ ưu tiên xử lý
                4. Đề xuất hành động cho bộ phận CSKH

                Nội dung phản hồi:
                %s
                """.formatted(feedbackText);

        ChatResponse chatResponse = chatModel.call(new Prompt(prompt));

        if (chatResponse == null
                || chatResponse.getResult() == null
                || chatResponse.getResult().getOutput() == null) {
            return "";
        }

        return chatResponse.getResult().getOutput().getText();
    }
}
```

## 2. Giải thích kiến trúc Loosely Coupled

`FeedbackAnalysisService` là service xử lý nghiệp vụ phân tích phản hồi khách hàng. Service này chỉ cần gọi một mô hình chat AI và nhận kết quả trả về. Nó không nên phụ thuộc trực tiếp vào việc mô hình đó là Ollama local hay OpenAI/OpenRouter cloud.

Vì vậy, service inject interface chung của Spring AI:

```java
private final ChatModel chatModel;
```

Thay vì inject implementation cụ thể:

```java
private final OllamaChatModel ollamaChatModel;
private final OpenAiChatModel openAiChatModel;
```

Đây là tư duy **Programming to Interface**:

- `FeedbackAnalysisService` phụ thuộc vào abstraction `ChatModel`, không phụ thuộc vào provider cụ thể.
- Khi đổi từ Ollama sang OpenAI/OpenRouter, không cần sửa code service.
- Việc chọn model AI được chuyển sang cấu hình Spring Boot.
- Code nghiệp vụ dễ kiểm thử hơn vì có thể mock interface `ChatModel`.
- Hệ thống dễ mở rộng nếu sau này thêm provider AI khác.

Nói cách khác, `FeedbackAnalysisService` chỉ biết "tôi cần một đối tượng có khả năng chat", còn Spring Boot quyết định đối tượng cụ thể nào sẽ được inject.

## 3. Xử lý lỗi trùng bean khi có cả Ollama và OpenAI starter

Khi project khai báo cả:

```gradle
implementation 'org.springframework.ai:spring-ai-starter-model-ollama'
implementation 'org.springframework.ai:spring-ai-starter-model-openai'
```

Spring có thể tạo nhiều bean cùng kiểu `ChatModel`. Khi đó, constructor của `FeedbackAnalysisService`:

```java
public FeedbackAnalysisService(ChatModel chatModel)
```

có thể gây lỗi vì Spring không biết nên inject bean Ollama hay OpenAI.

## Cách 1: Dùng `@Profile`

Ta có thể tạo cấu hình theo profile. Profile `local` dùng Ollama, profile `cloud` dùng OpenAI/OpenRouter.

```java
package com.rlogistics.cskh.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class AiRuntimeConfig {

    @Bean
    @Profile("local")
    public ChatModel chatModelForLocal(OllamaChatModel ollamaChatModel) {
        return ollamaChatModel;
    }

    @Bean
    @Profile("cloud")
    public ChatModel chatModelForCloud(OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }
}
```

Khi chạy local:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Khi chạy cloud:

```bash
export OPENROUTER_API_KEY="your_openrouter_api_key"
./gradlew bootRun --args='--spring.profiles.active=cloud'
```

Với cách này, mỗi môi trường chỉ kích hoạt một bean `ChatModel` phù hợp.

## Cách 2: Dùng `@Primary`

Nếu muốn chọn một bean mặc định trong trường hợp có nhiều bean cùng kiểu, có thể dùng `@Primary`.

```java
package com.rlogistics.cskh.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiRuntimeConfig {

    @Bean
    @Primary
    public ChatModel defaultChatModel(OllamaChatModel ollamaChatModel) {
        return ollamaChatModel;
    }
}
```

Khi `FeedbackAnalysisService` cần inject `ChatModel`, Spring sẽ ưu tiên bean có annotation `@Primary`.

Trong bài toán cần chuyển đổi linh hoạt giữa local và cloud, giải pháp `@Profile` thường rõ ràng và phù hợp hơn. `@Primary` phù hợp khi hệ thống có một model mặc định và các model khác chỉ dùng trong tình huống đặc biệt.
