import type { SiteConfig } from '../models/site-config-model';
import { formatMetadata } from './paint-search';

export function configuredLabel(config: SiteConfig, labelKey: string) {
  const [section, key] = labelKey.split('.');
  const configKey = key?.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase());
  const values = (config as unknown as Record<string, Record<string, unknown>>)[section];
  const label = values?.[configKey];
  return typeof label === 'string' ? label : formatMetadata(key ?? labelKey);
}

export function metadataLabel(config: SiteConfig, value: string) {
  const configKey = value.replace(/_([a-z])/g, (_, letter: string) => letter.toUpperCase());
  return config.collection.valueLabels[configKey] ?? config.collection.valueLabels[value]
    ?? config.collection.valueLabels[value.toLowerCase()] ?? formatMetadata(value);
}
