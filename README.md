# OmniTweaks

Mod modular de Quality of Life para **Minecraft 26.1.1** (Fabric).

## Módulos

| Comando | Descrição |
|---------|-----------|
| `/ot autoshulker` | Liga/desliga o AutoShulker |
| `/ot treecapitator` | Liga/desliga o TreeCapitator |
| `/ot drill` | Liga/desliga o OmniDrill (área 3×3 padrão) |
| `/ot drill <largura> <altura>` | Ativa o OmniDrill com área personalizada (1–8) |

O alias `/omnitweaks` também funciona para todos os comandos acima.

---

## Pré-requisitos

| Ferramenta | Versão mínima | Observação |
|------------|--------------|------------|
| **Java** | 25 | MC 26.1.1 exige Java 25 (`java-runtime-epsilon`) |
| **Git** | qualquer | Para clonar o repositório |
| **GitHub CLI (`gh`)** | qualquer | Opcional, mas recomendado para abrir PRs via terminal ou ao usar IAs como o Claude Code |

> **Não é necessário ter o Gradle instalado globalmente.** O projeto inclui o Gradle Wrapper (`gradlew.bat`).

---

## Como compilar

```bat
:: 1. Clone o repositório
git clone https://github.com/jovinull/OmniTweaks.git
cd OmniTweaks

:: 2. Compile (Windows)
gradlew.bat build

:: 3. O JAR gerado estará em:
::    build\libs\omnitweaks-1.0.0.jar
```

No primeiro build, o Gradle Wrapper baixará automaticamente o Gradle 9.4.0 e o Loom
baixará o Minecraft 26.1.1. Isso pode demorar alguns minutos dependendo da conexão.

---

## Como instalar o mod

1. Instale o [Fabric Loader 0.18.6](https://fabricmc.net/use/installer/) para Minecraft 26.1.1
2. Instale o [Fabric API 0.145.3+26.1.1](https://modrinth.com/mod/fabric-api)
3. Copie `build\libs\omnitweaks-1.0.0.jar` para a pasta `mods` do seu Minecraft

---

## Informações técnicas

| Dependência | Versão |
|-------------|--------|
| Minecraft | 26.1.1 |
| Fabric Loader | 0.18.6 |
| Fabric API | 0.145.3+26.1.1 |
| Fabric Loom (build) | 1.15.5 |
| Gradle Wrapper | 9.4.0 |

### Notas de build

- O MC 26.1.1 é distribuído **sem obfuscação** — não há linha `mappings` no `build.gradle`.  
  O plugin é declarado com o ID qualificado `net.fabricmc.fabric-loom` (não o alias `fabric-loom`)
  para ativar o modo sem remapeamento (`noIntermediateMappings`).
- O bytecode compilado usa `--release 25` para compatibilidade com o runtime do Minecraft.

---

## Compilação limpa (limpar cache)

```bat
gradlew.bat clean build --no-daemon
```

---

## Contribuindo

Contribuições são bem-vindas. Abra uma issue para discutir a ideia ou envie um pull request diretamente.

> **Dica para quem usa IA (Claude Code, Copilot, etc.):** instale o [GitHub CLI (`gh`)](https://cli.github.com/) e autentique com `gh auth login`. Com ele disponível no terminal, a IA consegue criar PRs, verificar a CI e listar issues diretamente — sem precisar abrir o navegador.

O projeto usa branch protection em `main`: todo código entra via PR e precisa passar pela CI antes do merge.

---

## Licença

Este projeto está licenciado sob a **MIT License** — veja o arquivo [LICENSE](LICENSE) para os termos completos.

Em resumo: você pode usar, copiar, modificar e distribuir este código livremente, inclusive em outros mods, desde que mantenha o aviso de copyright original.
