package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The geometry as data: Tapatan's equals v1's board, and a geometry refuses incoherent data. */
class GeometryTest {

    @Test
    void tapatanHasNinePointsAndTheEightLinesOfTheV1Board() {
        assertThat(Geometry.TAPATAN.points(), is(9));
        assertThat(Geometry.TAPATAN.lines(), is(List.of(
                List.of(0, 1, 2), List.of(3, 4, 5), List.of(6, 7, 8),
                List.of(0, 3, 6), List.of(1, 4, 7), List.of(2, 5, 8),
                List.of(0, 4, 8), List.of(2, 4, 6))));
        assertThat(Geometry.TAPATAN.lines().size(), is(8));
    }

    @Test
    void tapatanAdjacencyEqualsTheV1BoardPointByPoint() {
        for (int point = 0; point < 9; point++) {
            assertThat(Geometry.TAPATAN.neighbours(point), containsInAnyOrder(
                    Board.neighbours(point).toArray()));
        }
        assertThat(Geometry.TAPATAN.neighbours(4), containsInAnyOrder(0, 1, 2, 3, 5, 6, 7, 8));
        assertThat(Geometry.TAPATAN.neighbours(0), containsInAnyOrder(1, 3, 4));
        assertThat(Geometry.TAPATAN.neighbours(1), containsInAnyOrder(0, 2, 4));
    }

    @Test
    void isPointAndAreAdjacentAnswerFromTheData() {
        assertThat(Geometry.TAPATAN.isPoint(0), is(true));
        assertThat(Geometry.TAPATAN.isPoint(8), is(true));
        assertThat(Geometry.TAPATAN.isPoint(9), is(false));
        assertThat(Geometry.TAPATAN.isPoint(-1), is(false));
        assertThat(Geometry.TAPATAN.areAdjacent(0, 1), is(true));
        assertThat(Geometry.TAPATAN.areAdjacent(0, 2), is(false));
        assertThat(Geometry.TAPATAN.areAdjacent(4, 8), is(true));
    }

    @Test
    void aGeometryCanBeAnyShape() {
        Geometry chain = new Geometry(4,
                List.of(List.of(1), List.of(0, 2), List.of(1, 3), List.of(2)),
                List.of(List.of(0, 1, 2)));

        assertThat(chain.points(), is(4));
        assertThat(chain.areAdjacent(2, 3), is(true));
        assertThat(chain.areAdjacent(0, 3), is(false));
    }

    @Test
    void anAsymmetricAdjacencyIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Geometry(2,
                List.of(List.of(1), List.of()), List.of()));
    }

    @Test
    void anAdjacencyOrALineOutsideThePointsIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new Geometry(2,
                List.of(List.of(2), List.of()), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Geometry(3,
                List.of(List.of(), List.of(), List.of()), List.of(List.of(0, 1, 3))));
    }

    @Test
    void theAdjacencyListCountMustEqualThePointCount() {
        assertThrows(IllegalArgumentException.class, () -> new Geometry(3,
                new ArrayList<>(List.of(List.of(), List.of())), List.of()));
    }

    // Six Men's Morris: outer square 0..7 clockwise from the top left (corners even, middles odd),
    // inner square 8..15 in the same order; the joins are 1-9, 3-11, 5-13, 7-15.

    @Test
    void sixMenHasSixteenPointsAndTwentyEdges() {
        assertThat(Geometry.SIX_MEN.points(), is(16));
        int degrees = IntStream.range(0, 16).map(p -> Geometry.SIX_MEN.neighbours(p).size()).sum();
        assertThat(degrees / 2, is(20));
    }

    @Test
    void inSixMenEachCornerHasTwoNeighboursAndEachMiddleThree() {
        for (int point = 0; point < 16; point++) {
            boolean corner = point % 2 == 0;
            assertThat("point " + point, Geometry.SIX_MEN.neighbours(point).size(), is(corner ? 2 : 3));
        }
    }

    @Test
    void sixMenAdjacencyIsTheTwoRingsAndTheFourJoins() {
        for (int i = 0; i < 8; i++) {
            assertThat(Geometry.SIX_MEN.areAdjacent(i, (i + 1) % 8), is(true));
            assertThat(Geometry.SIX_MEN.areAdjacent(8 + i, 8 + (i + 1) % 8), is(true));
        }
        assertThat(Geometry.SIX_MEN.neighbours(1), containsInAnyOrder(0, 2, 9));
        assertThat(Geometry.SIX_MEN.neighbours(3), containsInAnyOrder(2, 4, 11));
        assertThat(Geometry.SIX_MEN.neighbours(5), containsInAnyOrder(4, 6, 13));
        assertThat(Geometry.SIX_MEN.neighbours(7), containsInAnyOrder(6, 0, 15));
        assertThat(Geometry.SIX_MEN.neighbours(0), containsInAnyOrder(1, 7));
        assertThat(Geometry.SIX_MEN.neighbours(8), containsInAnyOrder(9, 15));
        // no diagonals, no corner-to-corner joins
        assertThat(Geometry.SIX_MEN.areAdjacent(0, 8), is(false));
        assertThat(Geometry.SIX_MEN.areAdjacent(2, 10), is(false));
    }

    @Test
    void sixMenHasTheEightSidesAsLinesAndNoneOnAJoin() {
        assertThat(Geometry.SIX_MEN.lines(), containsInAnyOrder(
                List.of(0, 1, 2), List.of(2, 3, 4), List.of(4, 5, 6), List.of(6, 7, 0),
                List.of(8, 9, 10), List.of(10, 11, 12), List.of(12, 13, 14), List.of(14, 15, 8)));
        for (List<Integer> line : Geometry.SIX_MEN.lines()) {
            assertThat(Geometry.SIX_MEN.areAdjacent(line.get(0), line.get(1)), is(true));
            assertThat(Geometry.SIX_MEN.areAdjacent(line.get(1), line.get(2)), is(true));
        }
        for (int[] join : new int[][] {{1, 9}, {3, 11}, {5, 13}, {7, 15}}) {
            for (List<Integer> line : Geometry.SIX_MEN.lines()) {
                assertThat(line.contains(join[0]) && line.contains(join[1]), is(false));
            }
        }
    }

    @Test
    void everySixMenCornerLiesOnTwoLinesAndEveryMiddleOnOne() {
        for (int point = 0; point < 16; point++) {
            int p = point;
            long count = Geometry.SIX_MEN.lines().stream().filter(line -> line.contains(p)).count();
            assertThat("point " + point, count, is(point % 2 == 0 ? 2L : 1L));
        }
    }

    @Test
    void theSixteenSymmetriesOfSixMenMapLinesToLinesAndEdgesToEdges() {
        Set<List<Integer>> images = new HashSet<>();
        for (int rotation = 0; rotation < 4; rotation++) {
            for (boolean reflect : new boolean[] {false, true}) {
                for (boolean swap : new boolean[] {false, true}) {
                    List<Integer> map = new ArrayList<>();
                    for (int point = 0; point < 16; point++) {
                        int ring = point % 8;
                        int square = point / 8;
                        int r = reflect ? (8 - ring) % 8 : ring;
                        r = (r + 2 * rotation) % 8;
                        map.add((swap ? 1 - square : square) * 8 + r);
                    }
                    images.add(map);
                    for (List<Integer> line : Geometry.SIX_MEN.lines()) {
                        List<Integer> image = line.stream().map(map::get).toList();
                        assertThat(image + " is a line", isLine(image), is(true));
                    }
                    for (int a = 0; a < 16; a++) {
                        for (int b : Geometry.SIX_MEN.neighbours(a)) {
                            assertThat(Geometry.SIX_MEN.areAdjacent(map.get(a), map.get(b)), is(true));
                        }
                    }
                }
            }
        }
        assertThat(images.size(), is(16));
    }

    private static boolean isLine(List<Integer> candidate) {
        List<Integer> backwards = new ArrayList<>(candidate);
        java.util.Collections.reverse(backwards);
        return Geometry.SIX_MEN.lines().stream().anyMatch(line ->
                line.equals(candidate) || line.equals(backwards));
    }

    @Test
    void theDataIsImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> Geometry.TAPATAN.lines().add(List.of(0, 1, 2)));
        assertThrows(UnsupportedOperationException.class, () -> Geometry.TAPATAN.neighbours(0).add(8));
    }
}
