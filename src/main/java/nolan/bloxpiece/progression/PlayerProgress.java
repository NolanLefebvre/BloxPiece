package nolan.bloxpiece.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * Niveau + XP d'un joueur (XP = progression DANS le niveau actuel, pas un total cumulé).
 * Donnée immuable : toute modification crée une nouvelle valeur. Aucune dépendance aux autres mécaniques du mod.
 */
public record PlayerProgress(int level, long xp) {

    public static final PlayerProgress START = new PlayerProgress(LevelCurve.START_LEVEL, 0L);

    /** Sauvegarde sur disque (avec le joueur). */
    public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("level", LevelCurve.START_LEVEL).forGetter(PlayerProgress::level),
            Codec.LONG.optionalFieldOf("xp", 0L).forGetter(PlayerProgress::xp)
    ).apply(instance, PlayerProgress::new));

    /** Envoi serveur -> client (pour les interfaces). */
    public static final StreamCodec<ByteBuf, PlayerProgress> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PlayerProgress::level,
            ByteBufCodecs.VAR_LONG, PlayerProgress::xp,
            PlayerProgress::new
    );

    /** Protège contre une sauvegarde modifiée à la main : niveau dans [1, max], XP jamais négative. */
    public PlayerProgress {
        level = Mth.clamp(level, LevelCurve.START_LEVEL, LevelCurve.MAX_LEVEL);
        xp = Math.max(0L, xp);
    }

    /** Ajoute de l'XP et gère les passages de niveau (plusieurs d'un coup si besoin). */
    public PlayerProgress withXp(long amount) {
        if (amount <= 0L || LevelCurve.isMaxLevel(level)) {
            return this;
        }

        int newLevel = level;
        long newXp = xp + amount;
        while (!LevelCurve.isMaxLevel(newLevel)) {
            long needed = LevelCurve.xpForNextLevel(newLevel);
            if (newXp < needed) {
                break;
            }
            newXp -= needed;
            newLevel++;
        }
        if (LevelCurve.isMaxLevel(newLevel)) {
            newXp = 0L; // niveau maximum : plus d'XP à gagner
        }
        return new PlayerProgress(newLevel, newXp);
    }
}
