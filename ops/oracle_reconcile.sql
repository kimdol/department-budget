whenever sqlerror exit failure rollback
whenever oserror exit failure
set pagesize 0 feedback off heading off verify off echo off trimspool on linesize 400
set transaction read only;
select 'WORKSPACE|' || w.workspace_id || '|' || w.period_key || '|' || w.period_state || '|' ||
       w.allocation || '|' || w.committed || '|' || w.spent || '|' || (w.allocation-w.committed-w.spent) ||
       '|' || count(p.kind) || '|' || nvl(sum(p.committed_delta),0) || '|' || nvl(sum(p.spent_delta),0)
from budget_workspace w left join budget_posting p on w.workspace_id=p.workspace_id
group by w.workspace_id,w.period_key,w.period_state,w.allocation,w.committed,w.spent
order by w.workspace_id;
select 'DEPARTMENT|' || parent_workspace || '|' || workspace_id || '|' || organization_type
from budget_workspace where parent_workspace is not null order by parent_workspace,workspace_id;
select 'ALLOCATION|' || head_id || '|' || request_key || '|' || branch_id || '|' || period_key || '|' || amount
from branch_allocation order by head_id,request_key;
select 'POSTING|' || workspace_id || '|' || period_key || '|' || claim_id || '|' || kind ||
       '|' || committed_delta || '|' || spent_delta || '|' || nvl(original_kind,'-')
from budget_posting order by workspace_id,period_key,claim_id,kind;
select 'BALANCE|' || workspace_id || '|' || period_key || '|' || allocation || '|' || committed || '|' || spent
from budget_statement_balance order by workspace_id,period_key;
select 'LINE|' || workspace_id || '|' || period_key || '|' || category || '|' || claim_count || '|' || spent
from budget_statement_line order by workspace_id,period_key,category;
select 'POLICY|' || workspace_id || '|' || category || '|' || generation || '|' || evaluator_version ||
       '|' || rawtohex(standard_hash(formula,'SHA256')) || '|' || rawtohex(standard_hash(attendance_rule,'SHA256')) ||
       '|' || nvl(reviewed_draft_id,'-') || '|' || nvl(review_hash,'-')
from policy_publication order by workspace_id,category,generation;
select 'SCHEDULED|' || workspace_id || '|' || category || '|' || source_period || '|' || target_period || '|' || draft_id || '|' || review_hash
from scheduled_policy order by workspace_id,category;
select 'REPLACEMENT|' || workspace_id || '|' || original_id || '|' || replacement_id
from claim_replacement order by workspace_id,original_id;
select 'VERSION|' || workspace_id || '|' || series_id || '|' || version_no || '|' || claim_id
from claim_version order by workspace_id,series_id,version_no;
select 'OUTBOX|' || workspace_id || '|' || claim_id || '|' || sequence_no || '|' || event_id ||
       '|' || event_type || '|' || amount || '|' || attempts || '|' || delivered ||
       '|' || nvl(rawtohex(standard_hash(last_error,'SHA256')),'-') ||
       '|' || to_char(created_at,'YYYY-MM-DD"T"HH24:MI:SS.FF6')
from portal_outbox order by workspace_id,claim_id,sequence_no;
select 'CLAIM|' || workspace_id || '|' || claim_id || '|' || category || '|' || policy_generation ||
       '|' || evaluator_version || '|' || rawtohex(standard_hash(formula,'SHA256')) ||
       '|' || rawtohex(standard_hash(attendance_rule,'SHA256')) || '|' || approval_route ||
       '|' || estimated || '|' || days || '|' || approved || '|' || nvl(to_char(actual),'-') ||
       '|' || settled || '|' || status || '|' || review_round || '|' || applicant_id
from expense_claim order by workspace_id,claim_id;
select 'EVIDENCE|' || workspace_id || '|' || claim_id || '|' || evidence_type || '|' || receipt_amount
from claim_evidence order by workspace_id,claim_id,evidence_type;
select 'REVIEW|' || workspace_id || '|' || claim_id || '|' || review_round || '|' || event_type ||
       '|' || actor_role || '|' || amount || '|' || nvl(rawtohex(standard_hash(note,'SHA256')),'-') ||
       '|' || to_char(recorded_at,'YYYY-MM-DD"T"HH24:MI:SS.FF6') || '|' || nvl(actor_member_id,'-')
from claim_review_event order by workspace_id,claim_id,review_round,event_type;
exit success
