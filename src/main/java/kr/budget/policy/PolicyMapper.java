package kr.budget.policy;

import java.util.List;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PolicyMapper {
    record Scheduled(String category, String targetPeriod, String draftId, String reviewHash) {}

    @Select("select category,target_period,draft_id,review_hash from scheduled_policy where workspace_id=#{workspace} order by category")
    List<Scheduled> scheduled(String workspace);

    @Insert("""
        insert into scheduled_policy(workspace_id,category,target_period,draft_id,review_hash,source_period)
        select workspace_id,#{item.category},#{item.targetPeriod},#{item.draftId},#{item.reviewHash},period_key
        from budget_workspace where workspace_id=#{workspace}
        """)
    void schedule(String workspace, Scheduled item);

    @Delete("delete from scheduled_policy where workspace_id=#{workspace} and draft_id=#{draftId} and target_period=#{targetPeriod}")
    int unschedule(String workspace, String draftId, String targetPeriod);
    record ClaimSample(String claimId, String title, long estimated, int days, long policyGeneration,
                       String formula, String evaluatorVersion, kr.budget.claims.ApprovalRoute approvalRoute, long inboxOrder) {}

    @Select("""
        select claim_id,title,estimated,days,policy_generation,formula,evaluator_version,approval_route,inbox_order
        from expense_claim where workspace_id=#{workspace} and category=#{category} and inbox_order < #{before}
        order by inbox_order desc fetch first 51 rows only
        """)
    List<ClaimSample> samples(String workspace, String category, long before);

    record Draft(String draftId, String category, long baseGeneration, String formula, String evaluatorVersion,
                 Long sampleCost, Integer sampleDays, Long originalSupport, Long candidateSupport, String attendanceRule) {
        public String getReviewHash() {
            try {
                var digest = MessageDigest.getInstance("SHA-256");
                for (Object field : new Object[]{"policy-review-v1", draftId, category, baseGeneration, formula,
                        evaluatorVersion, attendanceRule, sampleCost, sampleDays, originalSupport, candidateSupport}) {
                    byte[] bytes = field == null ? new byte[0] : field.toString().getBytes(StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(4).putInt(field == null ? -1 : bytes.length).array());
                    digest.update(bytes);
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (NoSuchAlgorithmException error) {
                throw new IllegalStateException(error);
            }
        }
    }

    @Select("""
        select generation, formula,evaluator_version,attendance_rule from policy_publication where workspace_id=#{workspace} and category=#{category}
        order by generation desc fetch first 1 rows only
        """)
    PolicyVersion latest(String workspace, String category);

    @Select("select generation, formula,evaluator_version,attendance_rule from policy_publication where workspace_id=#{workspace} and category=#{category} order by generation desc")
    List<PolicyVersion> history(String workspace, String category);

    @Insert("""
        insert into policy_draft (workspace_id,draft_id,category,base_generation,formula,evaluator_version,
            sample_cost,sample_days,original_support,candidate_support,attendance_rule)
        values (#{workspace},#{id},#{category},#{generation},#{formula},#{version},
            #{cost},#{days},#{original},#{candidate},#{attendanceRule})
        """)
    void draft(String workspace, String id, String category, long generation, String formula, String version,
               long cost, int days, long original, long candidate, String attendanceRule);

    String DRAFT_COLUMNS = "draft_id,category,base_generation,formula,evaluator_version,sample_cost,sample_days,original_support,candidate_support,attendance_rule";

    @Select("select " + DRAFT_COLUMNS + " from policy_draft where workspace_id=#{workspace} and draft_id=#{id}")
    Draft findDraft(String workspace, String id);

    @Select("select " + DRAFT_COLUMNS + " from policy_draft where workspace_id=#{workspace} and category=#{category} order by draft_id")
    List<Draft> drafts(String workspace, String category);

    @Insert("""
        insert into policy_publication (workspace_id,category,generation,formula,evaluator_version,attendance_rule,reviewed_draft_id,review_hash)
        values (#{workspace},#{draft.category},#{generation},#{draft.formula},#{draft.evaluatorVersion},#{draft.attendanceRule},#{draft.draftId},#{reviewHash})
        """)
    void publish(String workspace, long generation, Draft draft, String reviewHash);
}
