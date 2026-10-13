package org.kevinkib.cardgames.threemenmorris.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    @Test
    void theDataIsImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> Geometry.TAPATAN.lines().add(List.of(0, 1, 2)));
        assertThrows(UnsupportedOperationException.class, () -> Geometry.TAPATAN.neighbours(0).add(8));
    }
}
