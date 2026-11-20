package kr.budget.claims;

import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface EvidenceMapper {
    record Document(String claimId, String evidenceType, long receiptAmount) {}

    @Insert("insert into claim_evidence (workspace_id,claim_id,evidence_type,receipt_amount) values (#{workspace},#{id},#{type},#{amount})")
    void attach(String workspace, String id, String type, long amount);

    @Insert("""
        insert into evidence_replacement (workspace_id,claim_id,evidence_type,version_no,previous_amount,replacement_amount,review_round)
        select #{workspace},#{id},#{type},coalesce(max(version_no),0)+1,#{previous},#{amount},#{round}
        from evidence_replacement where workspace_id=#{workspace} and claim_id=#{id} and evidence_type=#{type}
        """)
    void recordReplacement(String workspace,String id,String type,long previous,long amount,int round);

    @Update("update claim_evidence set receipt_amount=#{amount} where workspace_id=#{workspace} and claim_id=#{id} and evidence_type=#{type}")
    void replace(String workspace,String id,String type,long amount);

    @Select("select claim_id,evidence_type,receipt_amount from claim_evidence where workspace_id=#{workspace} and claim_id=#{id} order by evidence_type")
    List<Document> documents(String workspace, String id);

    @Select("select claim_id,evidence_type,receipt_amount from claim_evidence where workspace_id=#{workspace} and claim_id=#{id} and evidence_type=#{type}")
    Document find(String workspace, String id, String type);
}
