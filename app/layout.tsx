import type { Metadata } from 'next';
import { Geist, Geist_Mono } from 'next/font/google';
import './globals.css';

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

export const metadata: Metadata = {
  metadataBase: new URL('http://localhost:5173'),
  title: 'Nuancier — Mon atelier de peinture',
  description: 'Référentiel local de peintures et projets de figurines, avec guides par modèle.',
  openGraph: {
    title: 'Nuancier',
    description: 'Mon atelier de peinture pour figurines.',
    images: ['/og.png'],
  },
  twitter: {
    card: 'summary_large_image',
    title: 'Nuancier',
    description: 'Mon atelier de peinture pour figurines.',
    images: ['/og.png'],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="fr">
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
      >
        {children}
      </body>
    </html>
  );
}
