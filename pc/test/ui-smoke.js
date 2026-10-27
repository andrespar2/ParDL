'use strict';

/**
 * Prueba de humo de la interfaz de ParDL PC.
 * Abre la app con Electron, comprueba las vistas principales y la cierra.
 */
const { _electron: electron } = require('playwright-core');
const path = require('path');

(async () => {
  const app = await electron.launch({ args: ['.'], cwd: path.join(__dirname, '..') });
  const page = await app.firstWindow();
  await page.waitForTimeout(2500);

  const title = await page.title();
  if (title !== 'ParDL') throw new Error(`Título inesperado: ${title}`);
  console.log('  [OK] Ventana principal');

  for (const view of ['download', 'browser', 'providers', 'settings', 'about']) {
    await page.locator(`[data-view="${view}"]`).click();
    await page.waitForTimeout(150);
    const active = await page.locator(`#view-${view}`).evaluate((element) => element.classList.contains('active'));
    if (!active) throw new Error(`La vista ${view} no se activó`);
    console.log(`  [OK] Vista ${view}`);
  }

  await page.locator('[data-view="home"]').click();
  const cards = await page.locator('.feature-card').count();
  if (cards < 4) throw new Error(`Se esperaban 4 tarjetas de inicio, hay ${cards}`);
  console.log('  [OK] Inicio y tarjetas');

  await app.close();
  console.log('\nInterfaz lista.\n');
})().catch((error) => {
  console.error(`\n  [X] ${error.message}\n`);
  process.exit(1);
});
