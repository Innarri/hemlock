-- Evergreen DB patch 0825.data.bre_format.sql
--
-- Fix some templates that loop over bibs to not have duplicated/run-on titles
--
BEGIN;

-- check whether patch can be applied
SELECT evergreen.upgrade_deps_block_check('0825', :eg_version);

-- I think we shy away from modifying templates on existing systems, but this seems pretty safe...
UPDATE
    action_trigger.event_definition
SET
    template = replace(template,'[% FOR cbreb IN target %]','[% FOR cbreb IN target %][% title = '''' %]')
WHERE
    id IN (31,32);

COMMIT;
