export type GuideContent = { summary: string; steps: string[]; tips: string[] };
export type PaintUsageGuide = {
  paintUsageGuideId: string; title: string; brand: string; ranges: string[]; revision: number;
  originalLanguage: string; language: string; content: GuideContent;
  knowledgeStatus: string; reviewRequired: boolean; translationStatus: string;
  translationReviewRequired: boolean; sourceUrls: string[];
};
