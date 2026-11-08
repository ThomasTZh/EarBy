package com.thomas.passiveeartraining.training.session

import com.thomas.passiveeartraining.training.VoiceDurationCatalog
import com.thomas.passiveeartraining.training.provider.CompiledExercise
import com.thomas.passiveeartraining.training.provider.ConcreteExercise
import com.thomas.passiveeartraining.training.provider.ExerciseCompiler
import com.thomas.passiveeartraining.training.provider.ExerciseGenerator
import com.thomas.passiveeartraining.training.provider.ExerciseProvider
import com.thomas.passiveeartraining.training.provider.ProviderRegistry
import com.thomas.passiveeartraining.training.recipe.RecipeDefinition

/**
 * The production exercise supply: the registered provider's generator plus the shared compiler.
 *
 * The source owns the provider's running random stream; [configure] builds a generator from the
 * accepted Start, so replaying a recorded seed reproduces the same sequence. A reconfigure that
 * keeps the seed and kind continues the same stream under the edited configuration, so an edited
 * setting applies to the next item without replaying the items already heard. Timelines are
 * compiled under an explicit immutable recipe and retained by controller items; the source holds
 * no history or timeline cache.
 */
class ConfiguredExerciseSource(
    private val registry: ProviderRegistry = ProviderRegistry.PRODUCTION,
    private val voiceDurations: () -> VoiceDurationCatalog = {
        com.thomas.passiveeartraining.training.BundledVoiceDurations
    },
    private val compiler: ExerciseCompiler = ExerciseCompiler()
) : ExerciseSource {

    private var request: StartRequest? = null
    private var provider: ExerciseProvider? = null

    /**
     * The running generator, kept across a reconfigure.
     *
     * A sequence edit rebuilds its internal draw machinery on the same stream, so the next item
     * follows the items already heard instead of replaying them; a new seed replaces the stream.
     */
    private var generator: ExerciseGenerator? = null

    override fun configure(request: StartRequest) {
        startOrContinue(request, continueStream = false)
    }

    override fun reconfigure(request: StartRequest) {
        startOrContinue(request, continueStream = true)
    }

    private fun startOrContinue(request: StartRequest, continueStream: Boolean) {
        if (continueStream && this.request == request) return
        val previous = this.request
        val kind = request.setup.provider.kind
        val resolved = registry.providerFor(kind)
            ?: throw IllegalArgumentException("No provider is registered for '$kind'")
        // The controller resolves an absent seed once before configuring, so a source that still
        // sees null here is a programmer error rather than a session without a seed.
        val seed = requireNotNull(request.setup.seed) {
            "The accepted Start must carry a resolved seed"
        }
        this.request = request
        this.provider = resolved

        val sameSequence = continueStream &&
            previous?.setup?.seed == seed &&
            previous.setup.provider.kind == kind
        if (sameSequence && previous.setup.provider == request.setup.provider) {
            // The generated sequence cannot change: the running generator, its bag position and
            // every compiled timeline stay exactly as they were.
            return
        }
        if (sameSequence) {
            // An edit to the configuration continues the same random stream: the pairs already
            // heard are not replayed and the next item follows them.
            generator!!.reconfigure(request.setup.provider)
        } else {
            // A fresh Start, or an edit that asks for a new seed, starts the seed's stream from
            // its beginning.
            generator = resolved.createGenerator(request.setup.provider, seed)
        }
    }

    override fun next(): ConcreteExercise {
        val active = generator ?: throw IllegalStateException(
            "configure() must be called before next()"
        )
        return active.next()
    }

    override fun compile(exercise: ConcreteExercise, recipe: RecipeDefinition): CompiledExercise =
        compiler.compile(exercise, recipe, voiceDurations())
}
