package org.kevinkib.cardgames.mangola.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MangolaGeometryTest {

    private static List<String> names(List<Integer> pits) {
        return pits.stream().map(MangolaGeometry::name).toList();
    }

    @Test
    void sideALoopIsTheJavaScriptOrder() {
        assertEquals(List.of("a2", "b2", "c2", "d2", "e2", "f2", "g2", "h2",
                "h1", "g1", "f1", "e1", "d1", "c1", "b1", "a1"), names(MangolaGeometry.loop(Side.A)));
    }

    @Test
    void sideBLoopIsTheJavaScriptOrder() {
        assertEquals(List.of("a4", "b4", "c4", "d4", "e4", "f4", "g4", "h4",
                "h3", "g3", "f3", "e3", "d3", "c3", "b3", "a3"), names(MangolaGeometry.loop(Side.B)));
    }

    @Test
    void loopsAreClosedAndNeverLeaveTheirSide() {
        for (Side side : Side.values()) {
            List<Integer> loop = MangolaGeometry.loop(side);
            assertEquals(16, loop.size());
            assertEquals(16, new HashSet<>(loop).size());
            for (int i = 0; i < 16; i++) {
                int pit = loop.get(i);
                assertEquals(side, MangolaGeometry.owner(pit));
                assertEquals(loop.get((i + 1) % 16), MangolaGeometry.next(pit));
            }
        }
    }

    @Test
    void theTwoLoopsPartitionThe32Pits() {
        Set<Integer> all = new HashSet<>(MangolaGeometry.loop(Side.A));
        all.addAll(MangolaGeometry.loop(Side.B));
        assertEquals(32, all.size());
    }

    @Test
    void theLoopsTurnTheCornersWhereTheJavaScriptDoes() {
        assertEquals("h1", MangolaGeometry.name(MangolaGeometry.next(Boards.pit("h2"))));
        assertEquals("a2", MangolaGeometry.name(MangolaGeometry.next(Boards.pit("a1"))));
        assertEquals("h3", MangolaGeometry.name(MangolaGeometry.next(Boards.pit("h4"))));
        assertEquals("a4", MangolaGeometry.name(MangolaGeometry.next(Boards.pit("a3"))));
    }

    @Test
    void innerRowsAreRows2And3() {
        for (char column = 'a'; column <= 'h'; column++) {
            assertTrue(MangolaGeometry.isInner(Boards.pit(column + "2")));
            assertTrue(MangolaGeometry.isInner(Boards.pit(column + "3")));
            assertEquals(false, MangolaGeometry.isInner(Boards.pit(column + "1")));
            assertEquals(false, MangolaGeometry.isInner(Boards.pit(column + "4")));
        }
    }

    @Test
    void facingAndBehindForEveryColumn() {
        for (char c = 'a'; c <= 'h'; c++) {
            assertEquals(c + "3", MangolaGeometry.name(MangolaGeometry.facing(Boards.pit(c + "2"))));
            assertEquals(c + "4", MangolaGeometry.name(MangolaGeometry.behind(Boards.pit(c + "2"))));
            assertEquals(c + "2", MangolaGeometry.name(MangolaGeometry.facing(Boards.pit(c + "3"))));
            assertEquals(c + "1", MangolaGeometry.name(MangolaGeometry.behind(Boards.pit(c + "3"))));
        }
    }

    @Test
    void outerPitsHaveNoFacingNorBehindPit() {
        for (char c = 'a'; c <= 'h'; c++) {
            for (char row : new char[] {'1', '4'}) {
                assertEquals(MangolaGeometry.NONE, MangolaGeometry.facing(Boards.pit("" + c + row)));
                assertEquals(MangolaGeometry.NONE, MangolaGeometry.behind(Boards.pit("" + c + row)));
            }
        }
    }

    @Test
    void namesRoundTripOverThe32Pits() {
        Set<String> seen = new HashSet<>();
        for (int pit = 0; pit < MangolaGeometry.PITS; pit++) {
            String name = MangolaGeometry.name(pit);
            assertTrue(seen.add(name));
            assertEquals(pit, MangolaGeometry.pit(name));
        }
        assertEquals("a1", MangolaGeometry.name(0));
        assertEquals("h4", MangolaGeometry.name(31));
    }

    @Test
    void unknownNamesAndIndexesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> MangolaGeometry.pit("i1"));
        assertThrows(IllegalArgumentException.class, () -> MangolaGeometry.pit("a5"));
        assertThrows(IllegalArgumentException.class, () -> MangolaGeometry.pit("a"));
        assertThrows(IllegalArgumentException.class, () -> MangolaGeometry.name(32));
        assertThrows(IllegalArgumentException.class, () -> MangolaGeometry.name(-1));
    }
}
