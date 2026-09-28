package dev.settledlands;
import dev.settledlands.ritual.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RitualTest {
    @Test void sequenceIsTwoPlusFiveSeconds() {assertFalse(RitualRules.channeling(39));assertTrue(RitualRules.channeling(40));assertTrue(RitualRules.channeling(139));assertFalse(RitualRules.finished(139));assertTrue(RitualRules.finished(140));}
    @Test void timersAreIndependent() {assertTrue(RitualRules.locked(199,200));assertFalse(RitualRules.locked(200,200));assertTrue(RitualRules.locked(200,400));assertFalse(RitualRules.locked(400,400));}
    @Test void uploadedArmStartsInZombiePose() {assertEquals(90,RitualAnimation.sample("right_arm","rotation",0)[0]);}
    @Test void preservesFloatingHead() {assertEquals(8,RitualAnimation.sample("head","position",40)[1]);}
    @Test void preservesHeadScaling() {assertEquals(1.2,RitualAnimation.sample("head","scale",40)[0],1e-6);assertEquals(1.4,RitualAnimation.sample("head","scale",50)[0],1e-6);}
    @Test void idleLoopsEachSecond() {assertArrayEquals(RitualAnimation.sample("left_arm","rotation",45),RitualAnimation.sample("left_arm","rotation",65),1e-6);assertArrayEquals(RitualAnimation.sample("head","scale",55),RitualAnimation.sample("head","scale",115),1e-6);}
    @Test void emptyTracksHaveIdentityTransforms() {assertArrayEquals(new double[]{0,0,0},RitualAnimation.sample("body","rotation",50));assertArrayEquals(new double[]{1,1,1},RitualAnimation.sample("body","scale",50));}
    @Test void allFramesFiniteThroughoutBothClips() {
        for(double t=0;t<=140;t+=.1)for(String bone:new String[]{"head","left_arm","right_arm","body","left_leg","right_leg"})for(String channel:new String[]{"rotation","position","scale"})
            for(double v:RitualAnimation.sample(bone,channel,t))assertTrue(Double.isFinite(v));
    }
}
