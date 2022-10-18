# Contributing to Cryostat

Welcome to Cryostat 👋! First off, thanks for taking the time to contribute! 

All types of contributions are encouraged and valued. See the [Table of Contents](#table-of-contents) for different ways to contribute. Please make sure to read the relevant section before making your contribution. It will make it a lot easier for us maintainers and smooth out the experience for all involved. The community looks forward to your contributions. 🎉

## Table of Contents

- [I Have a Question](#i-have-a-question)
- [I Want To Contribute](#i-want-to-contribute)
  - [Report issues](#report-issues)
  - [Workflow](#workflow)

## I Have a Question

Before you ask a question, it is best to search for existing [Issues](https://github.com/cryostatio/cryostat/issues) that might help you. In case you have found a suitable issue and still need clarification, you can write your question in this issue. It is also advisable to search the internet for answers first.

If you then still feel the need to ask a question and need clarification, we recommend the following:

- Open an [Issue](https://github.com/cryostatio/cryostat/issues/new).
- Provide as much context as you can about what you're running into.
- Provide project and platform versions (`mvn`, `podman`, etc), depending on what seems relevant.
- Add a `question` label to your issue for easy search.

We will then take care of the issue as soon as possible.

## I Want To Contribute

### Report Issues

We use GitHub issues to track bugs and errors. If you run into an issue with the project:

- Open an [Issue](https://github.com/cryostatio/cryostat/issues/new).
- Explain the behavior you would expect and the actual behavior.
- Please provide as much context as possible and describe the *reproduction steps* that someone else can follow to recreate the issue on their own. This usually includes your code.

#### Good report tips

A good report shouldn't leave others needing to chase you up for more information. Therefore, we ask you to investigate carefully, collect information and describe the issue in detail in your report. If you are looking for support, you might want to check [this section](#i-have-a-question).

### Workflow

This is a simple guide of the workflow being used in Cryostat. We will use https://github.com/cryostatio/cryostat as an example.
#### Set up SSH with Github

See https://docs.github.com/en/authentication/connecting-to-github-with-ssh. If you prefer to use `https`, you can skip this step.

#### Fork the upstream repository

See https://docs.github.com/en/get-started/quickstart/fork-a-repo

#### Local changes

Clone the forked repository.
```bash
$ git clone git@github.com:<username>/cryostat.git
$ git remote add upstream git@github.com:cryostatio/cryostat.git # Adding upstream as remote
```

Navigate to `cryostat` directory and checkout a new branch.
```bash
$ cd cryostat && git checkout -b some-task-branch
```

Edit some files and commits your changes.
```bash
$ git add . # Stage your changes. Here, we stage the entire repo (recursively).
$ git commit -m "<type>(<scope>): a nice description of your work"
```

#### Open a PR

Push your changes to your remote respository (forked). You might need to rebase on the latest commit on the upstream's `main`.

To rebase:
```bash
$ git fetch upstream # Fetch upstream commits
$ git rebase upstream/main # Rebase on latest commit on upstream/main
```

To push:
```bash
$ git push -u origin some-task-branch # First time push
# or
$ git push -f origin some-task-branch # If already pushed to origin remote
```

Visit upstream repository and open a PR.

![visit-upstream](https://user-images.githubusercontent.com/68053619/193327052-9322651f-d325-429b-88d9-34133571282c.png)

Compare changes across forks (your branch in fork to the upstream main).

![select-compare-across-forks](https://user-images.githubusercontent.com/68053619/193328282-79d45b5e-097f-4995-ae21-51592dcf0bd4.jpg)


Add: 
- A title following [guidelines](https://www.conventionalcommits.org/en/v1.0.0/).
- A description with a referenced issue that the PR is for.
- A label (depending on your PR scope).
- A list of reviewers (at least one approval is required).

**Note**: You might not be able to add reviewers and labels. Don't worry! The project team will add them accordingly.

![prepare-pull-requests](https://user-images.githubusercontent.com/68053619/193328096-9df163d8-18ae-4978-b3c2-928ba4ad7872.jpg)

Once its done, you can click `Create Pull Request` and wait for approval.

![pull-requests-approved](https://user-images.githubusercontent.com/68053619/193328530-3cde0298-0254-4587-947a-43d51d02878f.png)

#### Result

Your changes will be get squashed into a single commit on `main` with the message including your PR title, and branch commit messages. From there, you can safely delete your branch.

To delete:
```bash
$ git checkout main # Must checkout a different branch before deleting the current checked out one
$ git branch -D some-task-branch # Delete local branch with force or without force using -d
$ git push --delete origin some-task-branch # Delete branch on origin (forked)
```

Now, you can rebase your local and origin `main` with latest upstream:
```bash
$ git fetch upstream
$ git checkout main
$ git rebase upstream/main
$ git push origin main # Update origin remote
```
