import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  site: 'https://motioncam-app.github.io',
  base: '/motioncam-remote-labs',
  integrations: [
    starlight({
      title: 'MotionCam Developer Docs',
      description: 'Developer documentation for MotionCam scripting and remote automation.',
      sidebar: [
        {
          label: 'Start Here',
          items: [
            { label: 'Overview', link: '/' },
          ],
        },
        {
          label: 'Scripting',
          items: [
            { label: 'JavaScript Engine', slug: 'scripting/overview' },
            { label: 'API Reference', slug: 'scripting/api-reference' },
            { label: 'Examples', slug: 'scripting/examples' },
          ],
        },
        {
          label: 'Remote Control',
          items: [
            { label: 'Native Client Pairing', slug: 'remote-control/native-client-pairing' },
            { label: 'Protocol', slug: 'remote-control/protocol' },
            { label: 'Remote Lab', link: '/remote-lab/' },
          ],
        },
      ],
    }),
  ],
});
