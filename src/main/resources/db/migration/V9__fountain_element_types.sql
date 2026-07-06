-- Blocks adopt the Fountain screenplay element taxonomy:
-- SCENE, ACTION, DIALOGUE, PARENTHETICAL, TRANSITION, LYRICS, CENTERED.
-- Fountain's default element is Action, so legacy TEXT blocks become ACTION.

UPDATE `block` SET `type` = 'ACTION' WHERE `type` = 'TEXT';
