import { expect, test } from '@playwright/test';

const user = process.env.TOTTUS_MAIN_SMOKE_USER
  ? JSON.parse(process.env.TOTTUS_MAIN_SMOKE_USER)
  : undefined;
const token = process.env.TOTTUS_MAIN_SMOKE_TOKEN;

test('main :4200 presenta sede fija y restaura la sede principal para usuarios no globales', async ({ page }) => {
  if (!token || !user) {
    test.skip(true, 'requires a short-lived read-only JWT for a non-global user');
    return;
  }
  test.skip(user.accesoGlobal === true, 'requires a non-global account');
  await page.addInitScript(({ jwt, account }) => {
    localStorage.setItem('tottus_token', jwt);
    localStorage.setItem('tottus_user', JSON.stringify(account));
    if (!localStorage.getItem('tottus_active_sede')) {
      localStorage.setItem('tottus_active_sede', String(account.idSede));
    }
  }, { jwt: token, account: user });

  await page.setViewportSize({ width: 1366, height: 900 });
  await page.goto('/dashboard');
  await expect(page.locator('.sede-value')).toBeVisible();
  await expect(page.locator('.sede-value')).toContainText(user.nombreSede);
  await expect(page.getByRole('combobox', { name: 'Sede activa' })).toHaveCount(0);

  const unauthorizedSite = [1, 2, 3, 4].find(id => id !== user.idSede);
  expect(unauthorizedSite).toBeTruthy();
  await page.evaluate(id => localStorage.setItem('tottus_active_sede', String(id)), unauthorizedSite);
  await page.reload();
  await expect.poll(() => page.evaluate(() => localStorage.getItem('tottus_active_sede'))).toBe(String(user.idSede));
  await expect(page.locator('.sede-value')).toContainText(user.nombreSede);
  await expect(page.getByRole('combobox', { name: 'Sede activa' })).toHaveCount(0);
});
