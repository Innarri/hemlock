BEGIN;

SELECT evergreen.upgrade_deps_block_check('0753', :eg_version);

CREATE OR REPLACE FUNCTION url_verify.parse_url (url_in TEXT) RETURNS url_verify.url AS $$

use Rose::URI;

my $url_in = shift;
my $url = Rose::URI->new($url_in);

my %parts = map { $_ => $url->$_ } qw/scheme username password host port path query fragment/;

$parts{full_url} = $url_in;
($parts{domain} = $parts{host}) =~ s/^[^.]+\.//;
($parts{tld} = $parts{domain}) =~ s/(?:[^.]+\.)+//;
($parts{page} = $parts{path}) =~ s#(?:[^/]*/)+##;

return \%parts;

$$ LANGUAGE PLPERLU;

CREATE OR REPLACE FUNCTION url_verify.ingest_url () RETURNS TRIGGER AS $$
DECLARE
    tmp_row url_verify.url%ROWTYPE;
BEGIN
    SELECT * INTO tmp_row FROM url_verify.parse_url(NEW.full_url);

    NEW.scheme          := tmp_row.scheme;
    NEW.username        := tmp_row.username;
    NEW.password        := tmp_row.password;
    NEW.host            := tmp_row.host;
    NEW.domain          := tmp_row.domain;
    NEW.tld             := tmp_row.tld;
    NEW.port            := tmp_row.port;
    NEW.path            := tmp_row.path;
    NEW.page            := tmp_row.page;
    NEW.query           := tmp_row.query;
    NEW.fragment        := tmp_row.fragment;

    RETURN NEW;
END;
$$ LANGUAGE PLPGSQL;

CREATE TRIGGER ingest_url_tgr
    BEFORE INSERT ON url_verify.url
    FOR EACH ROW EXECUTE PROCEDURE url_verify.ingest_url(); 

CREATE OR REPLACE FUNCTION url_verify.extract_urls ( session_id INT, item_id INT ) RETURNS INT AS $$
DECLARE
    last_seen_tag TEXT;
    current_tag TEXT;
    current_sf TEXT;
    current_url TEXT;
    current_ord INT;
    current_url_pos INT;
    current_selector url_verify.url_selector%ROWTYPE;
BEGIN
    current_ord := 1;

    FOR current_selector IN SELECT * FROM url_verify.url_selector s WHERE s.session = session_id LOOP
        current_url_pos := 1;
        LOOP
            SELECT  (XPATH(current_selector.xpath || '/text()', b.marc::XML))[current_url_pos]::TEXT INTO current_url
              FROM  biblio.record_entry b
                    JOIN container.biblio_record_entry_bucket_item c ON (c.target_biblio_record_entry = b.id)
              WHERE c.id = item_id;

            EXIT WHEN current_url IS NULL;

            SELECT  (XPATH(current_selector.xpath || '/../@tag', b.marc::XML))[current_url_pos]::TEXT INTO current_tag
              FROM  biblio.record_entry b
                    JOIN container.biblio_record_entry_bucket_item c ON (c.target_biblio_record_entry = b.id)
              WHERE c.id = item_id;

            IF current_tag IS NULL THEN
                current_tag := last_seen_tag;
            ELSE
                last_seen_tag := current_tag;
            END IF;

            SELECT  (XPATH(current_selector.xpath || '/@code', b.marc::XML))[current_url_pos]::TEXT INTO current_sf
              FROM  biblio.record_entry b
                    JOIN container.biblio_record_entry_bucket_item c ON (c.target_biblio_record_entry = b.id)
              WHERE c.id = item_id;

            INSERT INTO url_verify.url (session, item, url_selector, tag, subfield, ord, full_url)
              VALUES ( session_id, item_id, current_selector.id, current_tag, current_sf, current_ord, current_url);

            current_url_pos := current_url_pos + 1;
            current_ord := current_ord + 1;
        END LOOP;
    END LOOP;

    RETURN current_ord - 1;
END;
$$ LANGUAGE PLPGSQL;

COMMIT;

