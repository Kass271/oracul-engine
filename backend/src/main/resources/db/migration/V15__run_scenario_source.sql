ALTER TABLE generation_run ADD COLUMN scenario_run_id UUID NULL REFERENCES generation_run (id);
