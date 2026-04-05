package pl.viko.itemalchemyaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.pitan76.itemalchemy.EMCManager;

import java.util.ArrayList;
import java.util.List;

public class ReloadEmcCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("itemalchemyaddon")
                .then(CommandManager.literal("reloademc")
                        .executes(ReloadEmcCommand::reloadEmc)
                )
        );
    }

    private static int reloadEmc(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        source.sendMessage(Text.literal("Przeliczanie EMC z receptur..."));

        RecipeManager recipeManager = source.getServer().getRecipeManager();
        DynamicRegistryManager registryManager = source.getServer().getRegistryManager();

        // Pobierz wszystkie receptury określonych typów
        List<Recipe<?>> allRecipes = new ArrayList<>();
        allRecipes.addAll(recipeManager.listAllOfType(RecipeType.CRAFTING));
        allRecipes.addAll(recipeManager.listAllOfType(RecipeType.SMELTING));
        allRecipes.addAll(recipeManager.listAllOfType(RecipeType.BLASTING));
        allRecipes.addAll(recipeManager.listAllOfType(RecipeType.SMOKING));
        allRecipes.addAll(recipeManager.listAllOfType(RecipeType.CAMPFIRE_COOKING));

        // Debug: wyświetl liczbę znalezionych receptur
        source.sendMessage(Text.literal("Znaleziono " + allRecipes.size() + " receptur do przetworzenia"));

        List<Recipe<?>> processedRecipes = new ArrayList<>();
        List<Recipe<?>> unprocessedRecipes = new ArrayList<>(allRecipes);
        int successfulCalculations = 0;
        boolean changesMadeInPass;

        int pass = 0;
        do {
            pass++;
            changesMadeInPass = false;
            List<Recipe<?>> remainingRecipes = new ArrayList<>();

            for (Recipe<?> recipe : unprocessedRecipes) {
                if (processedRecipes.contains(recipe)) {
                    continue;
                }


                ItemStack output = recipe.getOutput(registryManager);
                if (output.isEmpty()) {
                    processedRecipes.add(recipe);
                    continue;
                }

                // Debug: wyświetl informacje o recepturze
                if (pass == 1) {
                    source.sendMessage(Text.literal("Przetwarzanie receptury: " + recipe.getId() + " -> " + output.getItem().getName().getString()));
                }


                // Sprawdź, czy przedmiot już ma EMC
                if(EMCManager.get(output.getItem())>0){
                    processedRecipes.add(recipe);
                    continue;
                }

                if (calculateEmcFromRecipe(output, recipe)) {
                    processedRecipes.add(recipe);
                    changesMadeInPass = true;
                    successfulCalculations++;
                    source.sendMessage(Text.literal("Dodano EMC dla: " + output.getName().getString() + " = " + EMCManager.get(output.getItem())));
                } else {
                    remainingRecipes.add(recipe);
                }
            }
            unprocessedRecipes = remainingRecipes;
        } while (changesMadeInPass && pass < 10);

        source.sendMessage(Text.literal("Zakończono przeliczanie EMC. Obliczono wartości dla " + successfulCalculations + " nowych przedmiotów."));
        return 1;
    }

    private static boolean calculateEmcFromRecipe(ItemStack output, Recipe<?> recipe) {
        long totalEmc = 0;
        boolean allIngredientsHaveEmc = true;

        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] matchingStacks = ingredient.getMatchingStacks();
            if (matchingStacks.length == 0) {
                continue;
            }

            // Znajdź pierwszy składnik, który ma wartość EMC
            boolean ingredientHasEmc = false;
            long ingredientEmc = 0;
            int ingredientCount = 0;

            for (ItemStack stack : matchingStacks) {
                if (EMCManager.contains(stack.getItem())) {
                    ingredientHasEmc = true;
                    ingredientEmc = EMCManager.get(stack.getItem());
                    ingredientCount = stack.getCount();
                    break;
                }
            }

            if (!ingredientHasEmc) {
                allIngredientsHaveEmc = false;
                break;
            }

            totalEmc += ingredientEmc * ingredientCount;
        }

        if (allIngredientsHaveEmc && totalEmc > 0) {
            int outputCount = output.getCount();
            long finalEmc = totalEmc / outputCount;

            if (finalEmc > 0) {
                EMCManager.set(output.getItem(), finalEmc);
                return true;
            }
        }

        return false;
    }
}