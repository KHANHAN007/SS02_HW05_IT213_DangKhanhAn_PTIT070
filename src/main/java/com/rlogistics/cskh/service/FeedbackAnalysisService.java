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
