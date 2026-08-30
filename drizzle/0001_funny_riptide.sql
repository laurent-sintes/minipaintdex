CREATE INDEX `idx_imports_status` ON `imports` (`status`);--> statement-breakpoint
CREATE INDEX `idx_paints_brand_name` ON `paints` (`brand`,`name`);--> statement-breakpoint
PRAGMA optimize;
