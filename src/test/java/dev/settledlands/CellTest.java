package dev.settledlands;
import java.util.Random;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CellTest {
    @Test void negativeCoordinatesFloorDown() {
        long key=Cell.at(new BlockPos(-1,-1,-1));
        assertEquals(new BlockPos(-1,-1,-1),BlockPos.of(key));
        assertEquals(new BlockPos(-16,-8,-16),Cell.min(key));
    }
    @Test void exactBoundaries() {
        assertEquals(Cell.key(1,1,1),Cell.at(new BlockPos(16,8,16)));
        assertEquals(Cell.key(0,0,0),Cell.at(new BlockPos(15,7,15)));
        assertEquals(Cell.key(-1,-1,-1),Cell.at(new BlockPos(-16,-8,-16)));
        assertEquals(Cell.key(-2,-2,-2),Cell.at(new BlockPos(-17,-9,-17)));
    }
    @Test void offsetsNeverChangeHeight() {
        long source=Cell.key(11,-6,7);
        assertEquals(Cell.key(10,-6,8),Cell.offset(source,-1,1));
    }
    @Test void worldBoundsRoundTrip() {
        Random r=new Random(1234);
        for(int i=0;i<10000;i++) {
            BlockPos p=new BlockPos(r.nextInt(60000001)-30000000,r.nextInt(1024)-512,r.nextInt(60000001)-30000000);
            BlockPos min=Cell.min(Cell.at(p));
            assertTrue(p.getX()>=min.getX()&&p.getX()<min.getX()+16);
            assertTrue(p.getY()>=min.getY()&&p.getY()<min.getY()+8);
            assertTrue(p.getZ()>=min.getZ()&&p.getZ()<min.getZ()+16);
        }
    }
}
