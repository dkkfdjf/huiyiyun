package com.huiyi.modules.knowledge;

/**
 * 识别 LLM 的「越界提问」拒绝作答,供 {@link RagService} 清来源 + {@link KbChatLogService} 打标记。
 *
 * <p>拒绝话术由作答 prompt 定死({@code SiliconFlowChatProvider.answer / answerGeneral}):<br>
 * "该内容不在知识库服务范围,请提出…" / "说明不在服务范围并引导提问"。<br>
 * 故可<b>确定性识别</b>(回答同时含「不在」与「服务范围」),而非去猜 LLM 的自由文本。
 *
 * <p>权衡:取「不在 + 服务范围」双命中而非单「服务范围」,避免误伤正经回答里偶然出现「服务范围」
 * (如某医保政策文),宁可漏判(变体拒绝落回"正常")也不误标正常作答为异常。
 */
public final class KbRefusalDetector {

    private KbRefusalDetector() {}

    public static boolean isRefusal(String answer) {
        if (answer == null) return false;
        String a = answer.trim();
        return a.contains("不在") && a.contains("服务范围");
    }
}
