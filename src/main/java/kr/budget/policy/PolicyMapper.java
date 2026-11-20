package kr.budget.policy;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PolicyMapper {
    record Draft(String draftId, String category, long baseGeneration, String formula, String evaluatorVersion,
                 Long sampleCost, Integer sampleDays, Long originalSupport, Long candidateSupport, String attendanceRule) {}

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

    @Insert("insert into policy_publication (workspace_id,category,generation,formula,evaluator_version,attendance_rule) values (#{workspace},#{category},#{generation},#{formula},#{version},#{attendanceRule})")
    void publish(String workspace, String category, long generation, String formula,String version,String attendanceRule);
}
