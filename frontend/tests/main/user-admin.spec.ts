import { expect, test } from '@playwright/test';

test('la administración de usuarios se limita al perfil administrador y adapta la lista a móvil', async ({ page }) => {
  await page.route('**/api/sedes', route => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify([{ idSede: 1, nombre: 'Tottus MegaPlaza', estado: true }])
  }));
  await page.route('**/api/admin/usuarios', route => route.fulfill({
    status: 200, contentType: 'application/json', body: '[]'
  }));
  await page.addInitScript(() => {
    localStorage.setItem('tottus_token', 'playwright-ui-only');
    localStorage.setItem('tottus_user', JSON.stringify({
      idUsuario: 1, usuario: 'admin.ui', rol: 'ADMINISTRADOR', idSede: 1,
      idSedesAutorizadas: [1], accesoGlobal: false, cambioPasswordRequerido: false
    }));
    localStorage.setItem('tottus_active_sede', '1');
  });

  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto('/usuarios');
  await expect(page.getByRole('heading', { name: 'Usuarios y permisos por sede' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Crear cuenta' })).toBeVisible();
  await expect(page.getByLabel('Nombre de usuario')).toBeVisible();
  await expect(page.locator('.site-choice').getByText('Tottus MegaPlaza', { exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
