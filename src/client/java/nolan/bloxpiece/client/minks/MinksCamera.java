package nolan.bloxpiece.client.minks;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import nolan.bloxpiece.fx.MinksFx;

/**
 * Danse des griffes éclair : CAMÉRA CINÉMATIQUE, uniquement chez le joueur qui lance l'attaque (rien n'est envoyé aux autres).
 * Pendant l'attaque, la vue passe à la troisième personne et la caméra (CameraMixin) se place à l'extérieur de la zone, un peu en
 * hauteur, derrière la position de départ du joueur, et tourne lentement autour de la zone en la regardant : on voit tout
 * l'enchaînement sans à-coups (elle ne suit pas les téléportations). Elle ne traverse jamais les murs (rapprochée si un bloc
 * est entre elle et le centre). À la fin de l'attaque, ou si elle est interrompue (mort, déconnexion, changement de race), la
 * perspective d'avant est restaurée exactement.
 */
public final class MinksCamera {

    /** Perspective du joueur avant l'attaque (null : rien à restaurer). */
    private static CameraType saved;
    private static Vec3 center;
    private static float radius = 5.0F, baseYaw;
    private static long startTick;

    private MinksCamera() {
    }

    /** À chaque tick client : entrée et sortie de la mise en scène, recherche de la zone (invisible) de l'attaque. */
    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        boolean active = player != null && MinksClient.isMinks(player) && MinksClient.dance(MinksClient.kind(player)) && player.isAlive();
        if (active) {
            if (saved == null) {
                saved = mc.options.getCameraType();
                center = player.position();
                baseYaw = player.getYRot();
                startTick = player.tickCount;
            }
            if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            // la zone invisible de cette attaque (MinksFx.ZONE) donne son centre et sa taille réels
            for (MinksFx fx : mc.level.getEntitiesOfClass(MinksFx.class, player.getBoundingBox().inflate(12.0),
                    e -> e.kind() == MinksFx.ZONE && e.shooter() == player.getId())) {
                center = fx.position();
                radius = fx.size();
                float[] d = fx.reach();
                baseYaw = (float) Math.toDegrees(Math.atan2(-d[0], d[2]));
            }
        } else if (saved != null) {
            mc.options.setCameraType(saved); // perspective d'avant, exactement
            saved = null;
            center = null;
        }
    }

    /**
     * Position et orientation de la caméra cinématique à cette image, ou null si elle n'est pas active :
     * {x, y, z, lacet, tangage}.
     */
    public static double[] cinematic(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (saved == null || center == null || player == null || mc.level == null) {
            return null;
        }
        float t = player.tickCount - startTick + partialTick;
        // coup final : la rotation s'arrête et le regard suit le joueur au-dessus du centre, pendant la chute, jusqu'à l'impact
        int kind = MinksClient.kind(player);
        boolean sulong = kind == nolan.bloxpiece.ability.Minks.DANCE_S;
        float e = MinksClient.elapsed(player, player.tickCount + partialTick);
        float ret = nolan.bloxpiece.ability.MinksDanceAbility.returnTick(sulong);
        float hover = MinksClient.ramp(e, ret, ret + 3.0F) * (1.0F - MinksClient.ramp(e, ret + nolan.bloxpiece.ability.MinksDanceAbility.DIVE,
                ret + nolan.bloxpiece.ability.MinksDanceAbility.FINAL_DELAY));
        float spin = Math.min(t, e >= 0.0F ? t - Math.max(0.0F, e - ret) : t); // (figée à partir du final)
        float yaw = baseYaw + 25.0F + spin * 0.55F; // en retrait de trois quarts, puis lente rotation autour de la zone
        double yr = Math.toRadians(yaw);
        Vec3 target = center.add(0.0, 1.0 + 3.5 * hover, 0.0); // (la chute finale part jusqu'à 7 blocs de haut)
        double dist = radius + 4.5 + 2.5 * hover, height = radius * 0.55 + 2.2 + 1.5 * hover; // recule pour cadrer la chute
        Vec3 want = target.add(Math.sin(yr) * dist, height, -Math.cos(yr) * dist);
        // jamais à travers un mur : rapprochée devant le premier bloc
        var hit = mc.level.clip(new ClipContext(target, want, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 back = want.subtract(target).normalize();
            want = hit.getLocation().subtract(back.scale(0.3));
        }
        Vec3 d = target.subtract(want);
        float camYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float camPitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new double[] {want.x, want.y, want.z, Mth.wrapDegrees(camYaw), camPitch};
    }
}
