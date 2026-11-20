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
select 'POSTING|' || workspace_id || '|' || period_key || '|' || claim_id || '|' || kind ||
       '|' || committed_delta || '|' || spent_delta || '|' || nvl(original_kind,'-')
from budget_posting order by workspace_id,period_key,claim_id,kind;
select 'BALANCE|' || workspace_id || '|' || period_key || '|' || allocation || '|' || committed || '|' || spent
from budget_statement_balance order by workspace_id,period_key;
select 'LINE|' || workspace_id || '|' || period_key || '|' || category || '|' || claim_count || '|' || spent
from budget_statement_line order by workspace_id,period_key,category;
exit success
