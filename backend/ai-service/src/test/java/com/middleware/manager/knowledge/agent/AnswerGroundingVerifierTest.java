package com.middleware.manager.knowledge.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 答案落地性校验（出口侧防幻觉）。
 * <p>幻觉的真实形态是**编造上下文里不存在的技术标识**——参数名、错误码、命令、阈值。
 * 输入侧的词法覆盖门禁抓不准这个（它会连同义表述的正确命中一起误杀），
 * 输出侧逐个核对标识符才是对症的信号。
 * <p>判定规则：答案中出现、但既不在上下文、也不在用户问题里的技术标识，视为凭空捏造。
 */
class AnswerGroundingVerifierTest {

    private final AnswerGroundingVerifier verifier = new AnswerGroundingVerifier();

    @Test
    @DisplayName("TC-GROUND-001 答案中的参数名在上下文有据时应判定为可信")
    void groundedAnswerPasses() {
        String context = "innodb_buffer_pool_size 建议设置为物理内存的 70%";
        String answer = "建议把 innodb_buffer_pool_size 设为物理内存的 70%。";

        assertThat(verifier.verify("缓存分配多少", answer, context).grounded()).isTrue();
    }

    @Test
    @DisplayName("TC-GROUND-002 答案编造上下文中不存在的参数名时应判定为不可信")
    void fabricatedParameterIsDetected() {
        String context = "innodb_buffer_pool_size 建议设置为物理内存的 70%";
        String answer = "还需要调整 innodb_log_file_size 与 max_connections 两个参数。";

        AnswerGroundingVerifier.GroundingResult result = verifier.verify("缓存分配多少", answer, context);

        assertThat(result.grounded()).isFalse();
        assertThat(result.ungroundedTokens()).contains("innodb_log_file_size", "max_connections");
    }

    @Test
    @DisplayName("TC-GROUND-003 答案复述用户问题里的标识不算捏造")
    void identifiersFromQuestionAreNotFabricated() {
        String context = "该错误与回滚段空间不足有关";
        String answer = "ORA-01555 的处理方式是扩大 UNDO 表空间。";

        // ORA-01555 来自用户提问，不是模型凭空造的
        assertThat(verifier.verify("ORA-01555 怎么处理", answer, context).ungroundedTokens())
                .doesNotContain("ORA-01555");
    }

    @Test
    @DisplayName("TC-GROUND-004 普通中文叙述不应被误判为捏造标识")
    void plainChineseAnswerIsNotFlagged() {
        String context = "主从延迟先查看复制状态";
        String answer = "先确认主从复制状态，再排查网络与磁盘。";

        assertThat(verifier.verify("主从延迟", answer, context).grounded()).isTrue();
    }

    @Test
    @DisplayName("TC-GROUND-005 通用知识说明不应被判定为幻觉")
    void generalKnowledgeNoticeIsGrounded() {
        assertThat(verifier.verify("任意问题",
                "以下建议基于通用技术知识，未引用内部资料。", "").grounded()).isTrue();
    }
}
