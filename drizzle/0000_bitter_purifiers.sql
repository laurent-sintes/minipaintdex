CREATE TABLE `imports` (
	`id` text PRIMARY KEY NOT NULL,
	`object_key` text NOT NULL,
	`filename` text NOT NULL,
	`content_type` text NOT NULL,
	`status` text DEFAULT 'a_verifier' NOT NULL,
	`created_at` text NOT NULL
);
--> statement-breakpoint
CREATE TABLE `paints` (
	`id` text PRIMARY KEY NOT NULL,
	`brand` text NOT NULL,
	`range` text,
	`reference` text,
	`name` text NOT NULL,
	`color_hex` text NOT NULL,
	`finish` text DEFAULT 'mat' NOT NULL,
	`medium` text DEFAULT 'acrylique' NOT NULL,
	`quantity` integer DEFAULT 1 NOT NULL,
	`tags` text DEFAULT '[]' NOT NULL,
	`notes` text DEFAULT '' NOT NULL,
	`created_at` text NOT NULL,
	`updated_at` text NOT NULL
);
