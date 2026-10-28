// French is available to players: a saved choice boots the whole UI in French, and the picker switches back.
describe('French language', () => {
  const visitInFrench = () => cy.visit('/', {
    onBeforeLoad(win) {
      win.localStorage.setItem('bc_locale', 'fr');
    },
  });

  it('A saved French choice sets the page language, the picker title and the lobby labels', () => {
    visitInFrench();
    cy.get('html').should('have.attr', 'lang', 'fr');
    cy.get('[data-test="language-button"]').should('have.attr', 'title', 'Langue');

    cy.openBatailleCorseLobby();
    cy.get('[data-test="add-bot"]').should('contain.text', 'Ajouter un bot');
    cy.get('[data-test="start"]').should('contain.text', 'Lancer la partie');
  });

  it('Switching language through the picker reloads the page in that language', () => {
    visitInFrench();
    cy.get('[data-test="language-button"]').click();
    cy.get('[data-test="language-option-en"]').click();
    cy.get('html').should('have.attr', 'lang', 'en');
    cy.get('[data-test="language-button"]').should('have.attr', 'title', 'Language');
    cy.window().its('localStorage').invoke('getItem', 'bc_locale').should('eq', 'en');
  });
});
