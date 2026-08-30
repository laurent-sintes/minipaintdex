import { index, integer, sqliteTable, text } from 'drizzle-orm/sqlite-core';

export const paints = sqliteTable('paints', {
  id: text('id').primaryKey(),
  brand: text('brand').notNull(),
  range: text('range'),
  reference: text('reference'),
  name: text('name').notNull(),
  colorHex: text('color_hex').notNull(),
  finish: text('finish').notNull().default('mat'),
  medium: text('medium').notNull().default('acrylique'),
  quantity: integer('quantity').notNull().default(1),
  tags: text('tags').notNull().default('[]'),
  notes: text('notes').notNull().default(''),
  createdAt: text('created_at').notNull(),
  updatedAt: text('updated_at').notNull(),
}, (table) => [
  index('idx_paints_brand_name').on(table.brand, table.name),
]);

export const imports = sqliteTable('imports', {
  id: text('id').primaryKey(),
  objectKey: text('object_key').notNull(),
  filename: text('filename').notNull(),
  contentType: text('content_type').notNull(),
  status: text('status').notNull().default('a_verifier'),
  createdAt: text('created_at').notNull(),
}, (table) => [
  index('idx_imports_status').on(table.status),
]);
